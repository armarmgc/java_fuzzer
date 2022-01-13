# java_fuzzer

A basic file format fuzzer written in Java.

## Setup
 - Put input files in inp_files/
 - Put target files in targets/
 - Create a `Run.java` that uses the Fuzzer class

### Test Setup
Copy files in `test_files/` to repository root.

```
cp -r test_files/* .
```

## Run
Compile and run fuzzer

```
javac Run.java
java Run
```

