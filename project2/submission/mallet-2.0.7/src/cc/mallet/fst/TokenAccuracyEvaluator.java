/* Copyright (C) 2002 Univ. of Massachusetts Amherst, Computer Science Dept.
   This file is part of "MALLET" (MAchine Learning for LanguagE Toolkit).
   http://www.cs.umass.edu/~mccallum/mallet
   This software is provided under the terms of the Common Public License,
   version 1.0, as published by http://www.opensource.org.  For further
   information, see the file `LICENSE' included with this distribution. */




/** 
   @author Andrew McCallum <a href="mailto:mccallum@cs.umass.edu">mccallum@cs.umass.edu</a>
 */

package cc.mallet.fst;


import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Sequence;

import cc.mallet.util.MalletLogger;

/**
 * Evaluates a transducer model based on predictions of individual tokens.
 */
public class TokenAccuracyEvaluator extends TransducerEvaluator
{
	private static Logger logger = MalletLogger.getLogger(TokenAccuracyEvaluator.class.getName());

    private Set<String> words = new HashSet<String>();
	private HashMap<String,Double> accuracy = new HashMap<String,Double>();
    private Map<String, Double> oovAccuracy = new HashMap<String, Double>();


    public TokenAccuracyEvaluator (InstanceList[] instanceLists, String[] descriptions) {
		super (instanceLists, descriptions);
	}
	
	public TokenAccuracyEvaluator (InstanceList instanceList1, String description1) {
		this (new InstanceList[] {instanceList1}, new String[] {description1});
	}
	
	public TokenAccuracyEvaluator (InstanceList instanceList1, String description1,
			InstanceList instanceList2, String description2) {
		this (new InstanceList[] {instanceList1, instanceList2}, new String[] {description1, description2});
	}
	
	public TokenAccuracyEvaluator (InstanceList instanceList1, String description1,
			InstanceList instanceList2, String description2,
			InstanceList instanceList3, String description3) {
		this (new InstanceList[] {instanceList1, instanceList2, instanceList3}, new String[] {description1, description2, description3});
	}

	public void evaluateInstanceList (TransducerTrainer trainer, InstanceList instances, String description) 
  {
		int numCorrectTokens;
		int totalTokens;
        int oovNumCorrectTokens;
        int oovTotalTokens;

		Transducer transducer = trainer.getTransducer();
		totalTokens = numCorrectTokens = oovNumCorrectTokens = oovTotalTokens = 0;
        int iteration = trainer.getIteration();

        boolean isTesting = description.equals("Testing");

		for (int i = 0; i < instances.size(); i++) {
			Instance instance = instances.get(i);
			Sequence input = (Sequence) instance.getData();
			Sequence trueOutput = (Sequence) instance.getTarget();
			assert (input.size() == trueOutput.size());
			//System.err.println ("TokenAccuracyEvaluator "+i+" length="+input.size());
			Sequence predOutput = transducer.transduce (input);
			assert (predOutput.size() == trueOutput.size());

			for (int j = 0; j < trueOutput.size(); j++) {
                if (!isTesting) {
                    words.add(input.get(j).toString());
                } else {
                    if (isTesting && !words.contains(input.get(j).toString())) {
                        if (trueOutput.get(j).equals(predOutput.get(j))) {
                            ++oovNumCorrectTokens;
                        }
                        oovTotalTokens++;
                    }
                }
				totalTokens++;
				if (trueOutput.get(j).equals(predOutput.get(j)))
					numCorrectTokens++;
			}
			//System.err.println ("TokenAccuracyEvaluator "+i+" numCorrectTokens="+numCorrectTokens+" totalTokens="+totalTokens+" accuracy="+((double)numCorrectTokens)/totalTokens);
		}
		double acc = ((double)numCorrectTokens)/totalTokens;
		//System.err.println ("TokenAccuracyEvaluator accuracy="+acc);
		accuracy.put(description, acc);
		//logger.info (description +" accuracy="+acc);
        logger.info(description + " accuracy=" + acc + " iteration=" + iteration + " IsTesting=" + isTesting);

        if (isTesting && oovTotalTokens != 0) {
            double tempAccuracy = ((double) oovNumCorrectTokens) / oovTotalTokens;
            oovAccuracy.put(description, tempAccuracy);
            logger.info(description + " AccuracyOOV=" + tempAccuracy + " NumCorrectTokens=" + oovNumCorrectTokens +
                    " OovTotalTokens=" + oovTotalTokens);
        }
	}

	/**
	 * Returns the accuracy from the last time test() or evaluate() was called
	 * @return
	 */
	public double getAccuracy (String description)
	{
		Double ret = accuracy.get(description);
		if (ret != null)
			return ret.doubleValue();
		throw new IllegalArgumentException ("No accuracy available for instance list \""+description+"\"");
	}
}
