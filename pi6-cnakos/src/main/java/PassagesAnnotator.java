import java.util.ArrayList;
import java.util.HashMap;

import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.EmptyFSList;
import org.apache.uima.jcas.cas.FSList;
import org.apache.uima.jcas.cas.NonEmptyFSList;
import org.apache.uima.jcas.cas.TOP;

import type.Passage;
import type.Question;

public class PassagesAnnotator extends JCasAnnotator_ImplBase {

	@Override
	public void process(JCas aJCas) throws AnalysisEngineProcessException {
		// Link Questions to Passages, because apparently neither QuestionAnnotator nor PassageAnnotator does this.
		// This annotator gets a stupid name because it's a stupid step to have to do.
		
		// Map question IDs to passages.
		HashMap<String, ArrayList<Passage>> passages = new HashMap<String, ArrayList<Passage>>();
		FSIterator pIter = aJCas.getAnnotationIndex(Passage.type).iterator();
		while (pIter.hasNext()) {
			Passage passage = (Passage) pIter.next();
			String qid = passage.getQuestionId();
			if (!passages.containsKey(qid)) {
				passages.put(qid, new ArrayList<Passage>());
			}
			passages.get(qid).add(passage);
		}
		
		// Link passages to respective questions.
		FSIterator qIter = aJCas.getAnnotationIndex(Question.type).iterator();
		while (qIter.hasNext()) {
			Question question = (Question) qIter.next();
			ArrayList<Passage> qPassages = passages.getOrDefault(question.getId(), new ArrayList<Passage>());
			if (qPassages.size() == 0)
				question.setPassages(new EmptyFSList(aJCas));
			else {
				NonEmptyFSList pList = new NonEmptyFSList(aJCas);
				NonEmptyFSList pOrigin = pList;
				for (int i = 0; i < qPassages.size(); i++) {
					pList.setHead(qPassages.get(i));
					if (i == qPassages.size() - 1) {
						pList.setTail(new EmptyFSList(aJCas));
					} else {
						pList.setTail(new NonEmptyFSList(aJCas));
						pList = (NonEmptyFSList) pList.getTail();
					}
				}
				question.setPassages(pOrigin);
			}
		}
	}

}
