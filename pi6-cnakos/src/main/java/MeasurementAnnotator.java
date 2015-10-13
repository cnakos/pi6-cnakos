import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.EmptyFSList;
import org.apache.uima.jcas.cas.FSList;
import org.apache.uima.jcas.cas.NonEmptyFSList;
import org.apache.uima.resource.ResourceInitializationException;

import edu.stanford.nlp.ling.Word;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.process.PTBTokenizer.PTBTokenizerFactory;
import type.Passage;
import type.Measurement;
import type.Question;

public class MeasurementAnnotator extends JCasAnnotator_ImplBase {
	
	public static final String PARAM_N = "N";
	public static final String PARAM_RANK_THRESHOLD = "RankThreshold";
	public static final String PARAM_SCORE_THRESHOLD = "ScoreThreshold";
	public static final String PARAM_THRESHOLD_MODE = "ThresholdMode";
	
	public static final String MODE_RANK_THRESHOLD = "RankThresholdMode";
	public static final String MODE_SCORE_THRESHOLD = "ScoreThresholdMode";
	
	private int mN = 1;
	private int mRankThreshold = 5;
	private float mScoreThreshold = 0.5f;
	private String mMode;

	@Override
	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
		mN = (int) aContext.getConfigParameterValue(PARAM_N);
		mRankThreshold = (int) aContext.getConfigParameterValue(PARAM_RANK_THRESHOLD);
		mScoreThreshold = (float) aContext.getConfigParameterValue(PARAM_SCORE_THRESHOLD);
		mMode = (String) aContext.getConfigParameterValue(PARAM_THRESHOLD_MODE);
	}
	
	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		// Score each Passage, rank them accordingly, and calculate the confusion matrix.
		
		// Iterate over the Questions in the CAS.
		FSIterator qIter = aJCas.getAnnotationIndex(Question.type).iterator();
		while (qIter.hasNext()) {
			// Assemble the passages for this question.
			Question question = (Question) qIter.next();
			ArrayList<Passage> passages = new ArrayList<Passage>();
			FSList pList = question.getPassages();
			while (pList != null && !(pList instanceof EmptyFSList)) {
				passages.add((Passage) ((NonEmptyFSList) pList).getHead());
				pList = ((NonEmptyFSList) pList).getTail();
			}
			
			// Score and rank the passages.
			HashMap<Integer, Double> scores = new HashMap<Integer, Double>();
			for (Passage passage : passages) {
				double score = score(question, passage);
				scores.put(passage.hashCode(), score);
			}
			Comparator<Passage> pComparator = new Comparator<Passage>() {
				public int compare(Passage o1, Passage o2) {
					double score1 = scores.getOrDefault(o1.hashCode(), 0.0);
					double score2 = scores.getOrDefault(o2.hashCode(), 0.0);
					if (score1 > score2)
						return -1;
					else if (score1 == score2)
						return 0;
					else
						return 1;
				}
			};
			Collections.sort(passages, pComparator);
			
			// Calculate confusion matrix.
			int tp = 0;
			int fp = 0;
			int fn = 0;
			int tn = 0;
			
			for (int i = 0; i < passages.size(); i++) {
				boolean label = passages.get(i).getLabel();
				if (mMode.equals(MODE_RANK_THRESHOLD)) {
					if (i < mRankThreshold && label)
						tp++;
					else if (i < mRankThreshold && !label)
						fp++;
					else if (i >= mRankThreshold && label)
						fn++;
					else
						tn++;
				} else if (mMode.equals(MODE_SCORE_THRESHOLD)) {
					if (passages.get(i).getScore() >= mScoreThreshold && label)
						tp++;
					else if (passages.get(i).getScore() >= mScoreThreshold && !label)
						fp++;
					else if (passages.get(i).getScore() < mScoreThreshold && label)
						fn++;
					else
						tn++;
				}
			}
						
			// Save measurements to Measurement object.
			Measurement measurement = new Measurement(aJCas);
			measurement.setTp(tp);
			measurement.setFp(fp);
			measurement.setFn(fn);
			// No TN recorded.
			measurement.addToIndexes();
			question.setMeasurement(measurement);
		}
	}
	
	public double score(Question question, Passage passage) {
		// Tokenizing the same question every time isn't very efficient.
		// For the time being, we'll let it slide.
		String[] qTokens = tokenize(question.getSentence());
		String[] pTokens = tokenize(passage.getText());
		
		// Calculate sum of percentages of Question n-grams in Passage for n = 1..N.
		// I may not keep this cumulative approach.
		double[] scores = new double[mN];
		for (int n = 1; n <= mN; n++) {
			scores[n-1] = ngram_sim(qTokens, pTokens, n);
		}
		double score = 0.0;
		for (int i = 0; i < scores.length; i++) {
			score += scores[i];
		}
		passage.setScore(score);
		
		return score;
	}
	
	public static double ngram_sim(String[] qTokens, String[] pTokens, int n) {
		// Calculate percentage of Question n-grams in Passage.
		// There are many more advanced ways to do this.		
		double score = 0.0;
		for (int i = 0; i < qTokens.length - n + 1; i++) {
			for (int j = 0; j < pTokens.length - n + 1; j++) {
				boolean mismatch = false;
				for (int k = 0; k < n; k++) {
					if (!qTokens[i + k].equals(pTokens[j + k])) {
						mismatch = true;
						break;
					}
				}
				if (!mismatch) {
					score += 1.0;
					break;
				}
			}
		}
		score /= qTokens.length - n + 1;
		return score;
	}
	
	// Return an array of Tokens added to the jcas corresponding to the given text.
	public static String[] tokenize(String text) {
		TokenizerFactory<Word> factory = PTBTokenizerFactory.newTokenizerFactory();
	    Tokenizer<Word> tokenizer = factory.getTokenizer(new StringReader(text));
		List<Word> words = tokenizer.tokenize();
		String[] tokens = new String[words.size()];
		for (int i = 0; i < words.size(); i++) {
			tokens[i] = Morphology.stemStatic(words.get(i).word(), null).word();
		}
		return tokens;
	}
}
