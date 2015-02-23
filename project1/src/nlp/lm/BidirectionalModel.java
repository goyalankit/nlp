package nlp.lm;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Created by ankit on 2/14/15.
 */
public class BidirectionalModel {


    public BigramModel bigramModel;
    public BackwardBigramModel backwardBigramModel;

    public static double FORWARD_RATIO = 0.5;
    public static double BACKWARD_RATIO = 1 - FORWARD_RATIO;

    /**
    *
    *  Initialize forward bigram model and backward model
    *
    * */
    public BidirectionalModel() {
        bigramModel = new BigramModel();
        backwardBigramModel = new BackwardBigramModel();
    }

    /**
     *
     *  Train both forward bigram model and backward bigram model
     *
    * */

    private void train(List<List<String>> trainSentences) {
        System.out.println("Training forward bigram model");
        bigramModel.train(trainSentences);

        System.out.println("Training backward bigram model");
        backwardBigramModel.train(trainSentences);
    }


    /* Compute log probability of sentence given current model */
    public double sentenceLogProb (List<String> sentence) {
        double [] bigramTokenProbs = bigramModel.sentenceTokenProbs(sentence);
        Collections.reverse(sentence);
        double [] backwardBigramTokenProbs = backwardBigramModel.sentenceTokenProbs(sentence);
        Collections.reverse(sentence);

        double sentenceLogProb = 0;
        for (int i = 0; i < bigramTokenProbs.length; i++) {
            double interProb = Math.log(FORWARD_RATIO * bigramTokenProbs[i] + BACKWARD_RATIO * backwardBigramTokenProbs[bigramTokenProbs.length - i - 1]);
            sentenceLogProb += interProb;
        }

        return sentenceLogProb;
    }


    /**
     * Test using the weights mentioned in the configuration.
     *
     */

    public void test (List<List<String>> sentences) {
        System.out.println("Testing on training data...");
        // Compute log probability of sentence to avoid underflow
        double totalLogProb = 0;

        // Keep count of total number of tokens predicted
        double totalNumTokens = 0;

        // Accumulate log prob of all test sentences
        for (List<String> sentence : sentences) {
            // Num of tokens in sentence plus 1 for predicting </S>
            totalNumTokens += sentence.size();
            double sentenceLogProb = sentenceLogProb(sentence);
            //      System.out.println(sentenceLogProb + " : " + sentence);
            // Add to total log prob (since add logs to multiply probs)
            totalLogProb += sentenceLogProb;
        }
        // Given log prob compute perplexity
        double perplexity = Math.exp(-totalLogProb / totalNumTokens);
        System.out.println("Perplexity = " + perplexity );
    }

    public static int wordCount (List<List<String>> sentences) {
        int wordCount = 0;
        for (List<String> sentence : sentences) {
            wordCount += sentence.size();
        }
        return wordCount;
    }

    /**
     * Train and test a Bidirectional Model model.
     * Command format: "nlp.lm.Bidirectional9Model [DIR]* [TestFrac]" where DIR
     * is the name of a file or directory whose LDC POS Tagged files should be
     * used for input data; and TestFrac is the fraction of the sentences
     * in this data that should be used for testing, the rest for training.
     * 0 < TestFrac < 1
     * Uses the last fraction of the data for testing and the first part
     * for training.
     */
    public static void main(String[] args) throws IOException {
        // All but last arg is a file/directory of LDC tagged input data
        File[] files = new File[args.length - 1];
        for (int i = 0; i < files.length; i++)
            files[i] = new File(args[i]);
        // Last arg is the TestFrac
        double testFraction = Double.valueOf(args[args.length -1]);
        // Get list of sentences from the LDC POS tagged input files
        List<List<String>> sentences = 	POSTaggedFile.convertToTokenLists(files);
        int numSentences = sentences.size();
        // Compute number of test sentences based on TestFrac
        int numTest = (int)Math.round(numSentences * testFraction);
        // Take test sentences from end of data
        List<List<String>> testSentences = sentences.subList(numSentences - numTest, numSentences);
        // Take training sentences from start of data
        List<List<String>> trainSentences = sentences.subList(0, numSentences - numTest);
        System.out.println("# Train Sentences = " + trainSentences.size() +
                " (# words = " + wordCount(trainSentences) +
                ") \n# Test Sentences = " + testSentences.size() +
                " (# words = " + wordCount(testSentences) + ")");

          // Create a bigram model and train it.
        BidirectionalModel model = new BidirectionalModel();
        System.out.println("Training...");
        model.train(trainSentences);
        // Test on training data using test and test2
        model.test(trainSentences);
//        model.test2(trainSentences);
        System.out.println("Testing...");
        // Test on test data using test and test2
        model.test(testSentences);
//        model.test2(testSentences);
    }
}

