class Run {
    public static void main(String[] args) {
        Fuzzer fuzzer1 = new Fuzzer(
            new String[] { "objdump", "-x" },    // Program and arguments
            "inp_files",                          // Directory with input files (corpus)
            "tmp_files",                      // Directory to store temporary files
            "logs",                              // Directory to store the log file
            "fuzzer_log"                         // Filename to store log file
        );

        Fuzzer fuzzer2 = new Fuzzer(
            new String[] { "readelf", "-l" },    // Program and arguments
            "inp_files",                          // Directory with input files (corpus)
            "tmp_files",                      // Directory to store temporary files
            "logs",                              // Directory to store the log file
            "fuzzer_log"                         // Filename to store log file
        );

        Fuzzer[] fuzzers = { fuzzer1, fuzzer2 };

        for (Fuzzer fuzzer : fuzzers) {
            for (int i = 0; i < 4; i++) {
                int thrId = i;

                new Thread(() -> {
                    fuzzer.worker(thrId, -1);
                }).start();
            }
        }

        try {
            for (;;) {
                Thread.sleep(1000);
                System.out.println(Fuzzer.getStats());
            }
        } catch (Exception e) {
            System.out.println(String.format("Sleep failed: %s"));
        }
    }
}

