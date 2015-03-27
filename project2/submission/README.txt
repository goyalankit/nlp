The submission.tar contains following files/folders:

1. Mallet: full mallet source code which contains changes in TokenAccuracyEvaluater.java
    - contains changes in `evaluateInstanceList` function.

```
Example command to run:

java -cp "mallet-2.0.7/class:mallet-2.0.7/lib/mallet-deps.jar" cc.mallet.fst.HMMSimpleTagger  --train true --model-file model_file --training-proportion 1.0 --test lab /Users/ankit/code/nlp/project1/data/pos/wsj/00false /Users/ankit/code/nlp/project1/data/pos/wsj/01false
```

2. preprocess: contains main method and POSTaggedFile.java that contains the parser code to generate the format required for mallet.
   - contains changes in segmentToken function.
   - can be called using the following format:
   ```
    java Main /Users/ankit/code/nlp/project1/data/pos/wsj/01 false false output

    where:
    /Users/ankit/code/nlp/project1/data/pos/wsj/01 is the path of input data files
    Boolean: false - tells if morph features need to be in output.
    Boolean: false - tells if suffix features should be in output
    output: is concated to the input file name to generate output file
   ```
