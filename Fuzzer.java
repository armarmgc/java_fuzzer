import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

public class Fuzzer {
    private String targetFile;
    private String options;
    private String inpDir;
    private ArrayList<Path> corpus;
    private String tmpDir;
    private String logDir;
    private String logFile;
    private Runtime runtime;
    private int fuzzerId;

    private double startTime = getTime();
    private double elapsedTime = 0.0;
    private double prevTime = 0.0;
    private AtomicInteger cases = new AtomicInteger(0);
    private AtomicInteger prevCases = new AtomicInteger(0);
    private AtomicInteger crashes = new AtomicInteger(0);

    private static ArrayList<Fuzzer> fuzzers = new ArrayList<Fuzzer>();
    private static ArrayList<String> targetFiles = new ArrayList<String>();
    private static ArrayList<String> stats = new ArrayList<String>();

    public Fuzzer(String[] cmd, String inpDir, String tmpDir, String logDir,
            String logFile) {
        this.targetFile = cmd[0];
        this.options = cmd[1];
        this.inpDir = inpDir;
        this.corpus = new ArrayList<Path>();
        this.tmpDir = tmpDir;
        this.logDir = logDir;
        this.logFile = logFile;
        this.runtime = Runtime.getRuntime();

        targetFiles.add(this.targetFile);
        stats.add("");

        this.fuzzerId = fuzzers.size();
        fuzzers.add(this);
    }

    public String getTargetFile() {
        return this.targetFile;
    }

    public void setTargetFile(String targetFile) {
        this.targetFile = targetFile;
    }

    public String getOptions() {
        return this.options;
    }

    public void setOptions(String options) {
        this.options = options;
    }

    public String getInpDir() {
        return this.inpDir;
    }

    public void setInpDir(String inpDir) {
        this.inpDir = inpDir;
    }

    public String getTmpDir() {
        return this.tmpDir;
    }

    public void setTmpDir(String tmpDir) {
        this.tmpDir = tmpDir;
    }

    public String getLogFile() {
        return this.logFile;
    }

    public void setLogFile(String logFile) {
        this.logFile = logFile;
    }

    public String getLogDir() {
        return this.logDir;
    }

    public void setLogDir(String logDir) {
        this.logDir = logDir;
    }

    public double getStartTime() {
        return startTime;
    }

    public double getElapsedTime() {
        return elapsedTime;
    }

    public int getCases() {
        return cases.get();
    }

    public int getCrashes() {
        return crashes.get();
    }

    public static ArrayList<String> getTargetFiles() {
        return targetFiles;
    }

    // Update statistics for fuzzer instance
    public void updateStats() {
        elapsedTime = getTime() - startTime;
        int numCases = cases.get();

        stats.set(
                fuzzerId,
                String.format("[ %s ] | %5d cases / %8.3f secs  =  %8.3f cps | %5d crashes |",
                    targetFile,
                    numCases,
                    elapsedTime,
                    (numCases - prevCases.get()) / (elapsedTime - prevTime) + 0,
                    crashes.get()
                )
        );
    }

    // Get statistics as a String
    public static String getStats() {
        for (Fuzzer fuzzer : fuzzers) {
            fuzzer.prevTime = fuzzer.elapsedTime;
            fuzzer.prevCases.set(fuzzer.cases.get());
        }

        return String.join("   ", stats);
    }

    // Wrapper function to get current time
    private double getTime() {
        return System.currentTimeMillis() / 1000.0;
    }

    // Fuzz target file a single time
    public void fuzz(int thrId, Path inpFile) throws IOException, InterruptedException {
        byte[] input = Files.readAllBytes(inpFile);

        // Mutate input randomly
        int numMutations = (int) (Math.random() * (8 + 1));

        for (int i = 0; i < numMutations; i++) {
            int idx = (int) (Math.random() * (input.length));
            byte randByte = (byte) (Math.random() * (255 + 1));

            input[idx] = randByte;
        }
            
        // Write mutated input to temporary input file
        String mutateFile = String.format(
            "%s/%s_inp_mutate%d",
            tmpDir,
            targetFile,
            thrId
        );
        Files.write(Paths.get(String.format(mutateFile)), input);

        // Run program with mutated file as input
        Process process = runtime.exec(
            String.format("./targets/%s %s %s",
                targetFile,
                options,
                mutateFile
            )
        );
        int exitCode = process.waitFor();

        // Record crash if found
        if (exitCode == 139) {
            int hash = Arrays.hashCode(input);

            System.out.println(String.format("[ %s ] Found crash - input `%s` - hash %08x", targetFile, inpFile.getFileName(), hash));
            crashes.getAndIncrement();

            // Log crash to file
            Files.write(
                Paths.get(
                    String.format("%s/%s", logDir, logFile)
                ),
                String.format(
                    "Crash on fuzz case %d, input - %s, hash - %x\n",
                    cases.get() + 1,
                    inpFile,
                    hash
                ).getBytes(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );

            // Log crashing input to `crashes` dir in log dir
            Files.write(
                Paths.get(
                    String.format("%s/%s_crashes/%s_crash_%08x",
                        logDir,
                        targetFile,
                        inpFile.getFileName(),
                        hash
                    )
                ),
                input
            );
        }
    }

    // Create a worker
    public void worker(int thrId, int numCases) {
        // Set start time if uninitialized
        if (startTime == 0) {
            startTime = getTime();
        }

        // Create initial directories
        try {
            Files.createDirectories(Paths.get(logDir, String.format("%s_crashes", targetFile)));
            Files.createDirectory(Paths.get(tmpDir));
        } catch (FileAlreadyExistsException e) {
            // We don't care if the directory is already made
        } catch (Exception e) {
            System.err.println(
                String.format("Failed to create directory: %s", e)
            );
        }

        // Generate corpus from input dir
        try {
            for (Path p : Files.newDirectoryStream(Paths.get(inpDir))) {
                corpus.add(p);
            }
        } catch (Exception e) {
            System.err.println(
                String.format("Failed to read input directory: %s", e)
            );
        }

        int corpusSize = corpus.size();
        
        // If numCases is negative, fuzzer will run forever
        for (int i = 0; i != numCases; i++) {
            // Run fuzz case with randomly chosen input file from corpus
            try {
                int idx = (int) (Math.random() * corpusSize);
                fuzz(thrId, corpus.get(idx));
        
                cases.getAndIncrement();

                updateStats();
            } catch (Exception e) {
                System.err.println(
                    String.format("Failed to run fuzz case: %s", e)
                );
            }
        }
    }
}

