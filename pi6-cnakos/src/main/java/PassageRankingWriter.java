import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.collection.CasConsumer_ImplBase;
import org.apache.uima.collection.CollectionException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.EmptyFSList;
import org.apache.uima.jcas.cas.FSList;
import org.apache.uima.jcas.cas.NonEmptyFSList;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;

import type.Measurement;
import type.Passage;
import type.Question;

/**
 * This CAS Consumer generates the report file with the method metrics
 */
public class PassageRankingWriter extends CasConsumer_ImplBase {
	final String PARAM_OUTPUTDIR = "OutputDir";

	final String OUTPUT_FILENAME = "ErrorAnalysis.csv";

	File mOutputDir;

	@Override
	public void initialize() throws ResourceInitializationException {
		String mOutputDirStr = (String) getConfigParameterValue(PARAM_OUTPUTDIR);
		if (mOutputDirStr != null) {
			mOutputDir = new File(mOutputDirStr);
			if (!mOutputDir.exists()) {
				mOutputDir.mkdirs();
			}
		}
	}

	@Override
	public void processCas(CAS arg0) throws ResourceProcessException {
		// Import the CAS as a aJCas
		JCas aJCas = null;
		File outputFile = null;
		PrintWriter writer = null;
		try {
			aJCas = arg0.getJCas();
			try {
				outputFile = new File(Paths.get(mOutputDir.getAbsolutePath(), OUTPUT_FILENAME).toString());
				outputFile.getParentFile().mkdirs();
				writer = new PrintWriter(outputFile);
			} catch (FileNotFoundException e) {
				System.out.printf("Output file could not be written: %s\n",
						Paths.get(mOutputDir.getAbsolutePath(), OUTPUT_FILENAME).toString());
				return;
			}

			writer.println("question_id,tp,fn,fp,precision,recall,f1");
			// Retrieve all the questions for printout
			List<Question> allQuestions = new ArrayList<Question>(JCasUtil.select(aJCas, Question.class));
			List<Question> subsetOfQuestions = RandomUtils.getRandomSubset(allQuestions, 10);

			// TODO: Here one needs to sort the questions in ascending order of their question ID
			Comparator<Question> qComparator = new Comparator<Question>() {
				public int compare(Question o1, Question o2) {
					return o1.getId().compareTo(o2.getId());
				}
			};
			Comparator<Passage> pComparator = new Comparator<Passage>() {
				public int compare(Passage o1, Passage o2) {
					if (o1.getScore() > o2.getScore())
						return -1;
					else if (o1.getScore() == o2.getScore())
						return 0;
					else
						return 1;
				}
			};
			Collections.sort(subsetOfQuestions, qComparator);

			for (Question q : subsetOfQuestions) {
				// For error analysis/debug.
				ArrayList<Passage> passages = new ArrayList<Passage>();
				FSList head = q.getPassages();
				while (!(head instanceof EmptyFSList)) {
					Passage passage = (Passage) ((NonEmptyFSList) head).getHead();
					passages.add(passage);
					head = ((NonEmptyFSList) head).getTail();
				}
				Collections.sort(passages, pComparator);
				/*for (Passage passage : passages) {
					String output = String.format("%s %s %d %.3f %s", q.getId(), passage.getSourceDocId(),
							passage.getLabel() ? 1 : 0, passage.getScore(), passage.getText());
					System.out.println(output);
				}*/
    	  
				Measurement m = q.getMeasurement();

				// TODO: Calculate actual precision, recall and F1
				double precision = m.getTp() + m.getFp() > 0.0 ? ((double) m.getTp()) / (m.getTp() + m.getFp()) : 0.0;
				double recall = m.getTp() + m.getFn() > 0.0 ? ((double) m.getTp()) / (m.getTp() + m.getFn()) : 0.0;
				double f1 = precision + recall > 0.0 ? 2 * precision * recall / (precision + recall) : 0.0;

				writer.printf("%s,%d,%d,%d,%.3f,%.3f,%.3f\n", q.getId(), m.getTp(), m.getFn(), m.getFp(),
						precision, recall, f1);
			}
		} catch (CASException e) {
			try {
				throw new CollectionException(e);
			} catch (CollectionException e1) {
				e1.printStackTrace();
			}
		} finally {
			if (writer != null)
				writer.close();
		}
	}
}
