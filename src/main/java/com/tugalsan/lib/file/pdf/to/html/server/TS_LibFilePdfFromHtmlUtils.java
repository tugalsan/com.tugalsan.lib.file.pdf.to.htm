package com.tugalsan.lib.file.pdf.to.html.server;


public class TS_FilePdfSignUtils {

    final private static TS_Log d = TS_Log.of(true, TS_FilePdfSignUtils.class);

    public static Path getPossibleDriverPath() {
        return List.of(File.listRoots()).stream()
                .map(p -> Path.of(p.toString()))
                .map(p -> p.resolve("bin"))
                .map(p -> p.resolve(TS_FilePdfSignUtils.class.getPackageName()))
                .map(p -> p.resolve("home"))
                .map(p -> p.resolve("target"))
                .map(p -> p.resolve(TS_FilePdfSignUtils.class.getPackageName() + "-1.0-SNAPSHOT-jar-with-dependencies.jar"))
                .filter(p -> TS_FileUtils.isExistFile(p))
                .findAny().orElse(null);
    }

    public static Path getHtmlPath(Path rawPdf) {
        var label = TS_FileUtils.getNameLabel(rawPdf);
        return rawPdf.resolveSibling(label + ".html");
    }

    public static Path getConfigPdfPath(Path rawPdf) {
//        var label = TS_FileUtils.getNameLabel(rawPdf);
        return rawPdf.resolveSibling("config.properties");
    }

    @Deprecated //NOT WORKING!
    public static TGS_UnionExcuse<Boolean> isSignedBefore(Path pdf) {
        return TGS_UnSafe.call(() -> {
            try (var doc = Loader.loadPDF(pdf.toFile())) {
                return TGS_UnionExcuse.of(!doc.getSignatureDictionaries().isEmpty());
            }
        }, e -> TGS_UnionExcuse.ofExcuse(e));
    }

    public static Properties config(Path pdfInput) {
        var props = new Properties();
        props.setProperty("inpdf.file", pdfInput.toAbsolutePath().toString());
        return props;
    }

    public static TGS_UnionExcuse<Path> makePdf(Path driver, Path pdfInput) {
        return TGS_UnSafe.call(() -> {
             d.ci("makePdf", "pdfInput", pdfInput);
            //CREATE TMP-INPUT BY MAIN-INPUT
            var tmp = Files.createTempDirectory("tmp").toAbsolutePath();
            var _pdfInput = tmp.resolve("_pdfInput.pdf");
            TS_FileUtils.copyAs(pdfInput, _pdfInput, true);

            //IF DONE, COPY TMP-OUTPUT TO MAIN-OUTPUT
            var u = _makePdf(driver, cfgSssl, cfgDesc, _pdfInput);
            if (u.isExcuse()) {
                return u.toExcuse();
            }
            var pdfOutput = getSignedPdfPath(pdfInput);
            TS_FileUtils.copyAs(u.value(), pdfOutput, true);

            return TGS_UnionExcuse.of(pdfOutput);
        }, e -> TGS_UnionExcuse.ofExcuse(e));
    }

    private static TGS_UnionExcuse<Path> _makePdf(Path driver, TS_FilePdfSignCfgSsl cfgSssl, TS_FilePdfSignCfgDesc cfgDesc, Path pdfInput) {
        var outputPdf = getSignedPdfPath(pdfInput);
        d.ci("_makePdf", "outputPdf", outputPdf);
        var configPdf = getConfigPdfPath(pdfInput);
        d.ci("_makePdf", "configPdf", configPdf);
        TS_FilePropertiesUtils.write(config(cfgSssl, cfgDesc, pdfInput), configPdf);
        return TGS_UnSafe.call(() -> {
            d.ci("_makePdf", "cfgSssl", cfgSssl);
            d.ci("_makePdf", "cfgDesc", cfgDesc);
            d.ci("_makePdf", "rawPdf", pdfInput);
            //CHECK IN-FILE
            if (pdfInput == null || !TS_FileUtils.isExistFile(pdfInput)) {
                return TGS_UnionExcuse.ofExcuse(d.className, "_makePdf", "input file not exists-" + pdfInput);
            }
            if (TS_FileUtils.isEmptyFile(pdfInput)) {
                return TGS_UnionExcuse.ofExcuse(d.className, "_makePdf", "input file is empty-" + pdfInput);
            }
            //CHECK OUT-FILE
            TS_FileUtils.deleteFileIfExists(outputPdf);
            if (TS_FileUtils.isExistFile(outputPdf)) {
                return TGS_UnionExcuse.ofExcuse(d.className, "_makePdf", "output file cleanup error-" + outputPdf);
            }
            //SIGN
            List<String> args = new ArrayList();
            args.add("\"" + TS_OsJavaUtils.getPathJava().resolveSibling("java.exe") + "\"");
            args.add("-jar");
            args.add("\"" + driver.toAbsolutePath().toString() + "\"");
            args.add("--load-properties-file");
            args.add("\"" + configPdf.toAbsolutePath().toString() + "\"");
            d.cr("_makePdf", "args", args);
            var cmd = args.stream().collect(Collectors.joining(" "));
            d.cr("_makePdf", "cmd", cmd);
            var p = TS_OsProcess.of(args);
            //CHECK OUT-FILE
            if (!TS_FileUtils.isExistFile(outputPdf)) {
                d.ce("_makePdf", "cmd", p.toString());
                return TGS_UnionExcuse.ofExcuse(d.className, "_makePdf", "output file not created-" + outputPdf);
            }
            if (TS_FileUtils.isEmptyFile(outputPdf)) {
                d.ce("_makePdf", "cmd", p.toString());
                TS_FileUtils.deleteFileIfExists(outputPdf);
                return TGS_UnionExcuse.ofExcuse(d.className, "_makePdf", "output file is empty-" + outputPdf);
            }
            //RETURN
            d.cr("_makePdf", "returning outputPdf", outputPdf);
            return TGS_UnionExcuse.of(outputPdf);
        }, e -> {
            //HANDLE EXCEPTION
            d.ce("_makePdf", "HANDLE EXCEPTION...");
            TS_FileUtils.deleteFileIfExists(outputPdf);
            return TGS_UnionExcuse.ofExcuse(e);
        }, () -> TS_FileUtils.deleteFileIfExists(configPdf));
    }
}
