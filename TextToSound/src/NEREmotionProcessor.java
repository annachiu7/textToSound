import java.io.BufferedReader;
import java.io.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import edu.stanford.nlp.io.*;
import edu.stanford.nlp.pipeline.*;

public class NEREmotionProcessor {

	// --------------------------------------------------------
	// Members
	// --------------------------------------------------------
	private String mSrcFileName;
	private Integer sections;
	private static Integer TextLength = 0;

	//private List<NERElement> NNList = new ArrayList<>(); // List with Nouns
	private List<NERElement> NNPList = new ArrayList<>(); // List with Proper Nouns (Names)
	private List<NERElement> AdjList = new ArrayList<>(); // List with Adjectives

	// --------------------------------------------------------
	// Constructors
	// --------------------------------------------------------

	public NEREmotionProcessor() {
	};

	/**
	 * Constructor.
	 * 
	 * @param src
	 *            source text to be analysed
	 * @param sections
	 *            number of sections
	 */
	public NEREmotionProcessor(String src, Integer sec) {
		mSrcFileName = src;
		sections = sec;
	};

	// --------------------------------------------------------
	// Methods
	// --------------------------------------------------------

	public List<NERElement> getAdjList() {
		return AdjList;
	}

	// *Create temp text file of text without punctuation
	public static void prepareText(String story, String tempstory) {

		try {
			// System.out.println(story);
			FileReader reader = new FileReader(story);
			BufferedReader bufferedReader = new BufferedReader(reader);
			FileWriter writer = new FileWriter(tempstory, true);

			String line;

			while ((line = bufferedReader.readLine()) != null) {
				String text = line.replaceAll(".,", " ");
				text = line.replaceAll("[^a-zA-Z ]", "");
				writer.write(text + " ");

			}
			writer.close();
			reader.close();

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// *Run Stanford Classifier and create XMl-File

	public static void create_xml(String story) throws IOException {

		String storyXML = story + ".xml";
		PrintWriter xmlOut = new PrintWriter(storyXML);

		Properties props = new Properties();
		props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner");

		StanfordCoreNLP pipeline = new StanfordCoreNLP(props);

		Annotation annotation = new Annotation(IOUtils.slurpFileNoExceptions(story));

		pipeline.annotate(annotation);

		pipeline.xmlPrint(annotation, xmlOut);

	}

	public static void classify(String story) {
		try {
			Runtime rt = Runtime.getRuntime();
			Process pr = rt.exec(
					"java -cp \"data/stanford-corenlp/*\" -Xmx2g edu.stanford.nlp.pipeline.StanfordCoreNLP -annotators tokenize,ssplit,pos,lemma,ner -file "
							+ story + " -outputDirectory data/lit/");

			int exitVal = pr.waitFor();
			System.out.println("Exited with error code " + exitVal);
		} catch (Exception e) {
			System.out.println("it does not work :(");
			e.printStackTrace();
		}
	}

	// *Get important information from xml-file
	public static Integer analyze_xml(String story, List<NERElement> NNPList, Integer TextLength) {
		Attribute SidAttr = null; // Sentence ID
		Attribute TidAttr = null; // Word ID
		String TName = null; // Word Name
		String category; // temporary variable to classify token for the two lists
		NERElement NEREle = null;

		try {
			XMLInputFactory factory = XMLInputFactory.newInstance();
			XMLEventReader eventReader = factory.createXMLEventReader(new FileReader(story + ".xml")); // read in the
																										// xml file

			while (eventReader.hasNext()) { // while there are still elements unread in xml
				XMLEvent event = eventReader.nextEvent();

				if (event.isStartElement()) {
					StartElement startElement = event.asStartElement();
					if (startElement.getName().getLocalPart().equals("sentence")) {// find element tagged as "sentence"
						SidAttr = startElement.getAttributeByName(new QName("id")); // define id attribute

					} else if (startElement.getName().getLocalPart().equals("token")) {// find element tagged as "token"
						TidAttr = startElement.getAttributeByName(new QName("id")); // define id attribute
						if (TidAttr != null) {
							TextLength++; // if Token has ID, increase count variable
						}
					} else if (startElement.getName().getLocalPart().equals("word")) {
						event = eventReader.nextEvent();
						TName = String.valueOf(event.asCharacters().getData());

					} else if (startElement.getName().getLocalPart().equals("POS")) {
						event = eventReader.nextEvent();
						category = String.valueOf(event.asCharacters().getData());
						

						if (Objects.equals(category, "NNP")) {
							NEREle = new NERElement();
							NEREle.setName(TName);
							NEREle.setTokenID(Integer.parseInt(TidAttr.getValue()));
							NEREle.setSentenceID(Integer.parseInt(SidAttr.getValue()));
							NEREle.setTotalPosition(TextLength);
							NNPList.add(NEREle);
						} else if (Objects.equals(category, ".") || Objects.equals(category, ",")
								|| Objects.equals(category, "``") || Objects.equals(category, "''")
								|| Objects.equals(category, "\"") || Objects.equals(category, "POS")
								|| Objects.equals(category, "'") || Objects.equals(category, ";")
								|| Objects.equals(category, "/") || Objects.equals(category, ":")
								|| Objects.equals(category, "[") || Objects.equals(category, "]")
								|| Objects.equals(category, "(") || Objects.equals(category, ")")
								|| Objects.equals(category, "{") || Objects.equals(category, "}")
								|| Objects.equals(category, "?") || Objects.equals(category, "!")
								|| Objects.equals(category, "`") || Objects.equals(category, "´")
								|| Objects.equals(category, "-") || Objects.equals(category, "_")
								|| Objects.equals(category, "CD") || Objects.equals(category, "SYM")) {
							TextLength--; // ignore punctuation for total text length
						}
					}
				}
			}

			for (int Element = 0; Element < NNPList.size(); Element++) {
				NNPList.get(Element).setRelativePosition(
						Math.round(NNPList.get(Element).getTotalPosition() / TextLength * 10000D) / 100D);
			}

			for (int Element = 0; Element < NNPList.size() - 1; Element++) {
				int index = 0;
				while (Element + index < NNPList.size() - 1
						&& NNPList.get(Element + index).getSentenceID() == NNPList.get(Element + index + 1)
								.getSentenceID()
						&& NNPList.get(Element + index).getTokenID() == NNPList.get(Element + index + 1).getTokenID()
								- 1) {
					NNPList.get(Element)
							.setName(NNPList.get(Element).getName() + " " + NNPList.get(Element + index + 1).getName());
					index++;

				}
				for (int i = 0; i < index; i++) {
					NNPList.remove(Element + 1);
				}

			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (XMLStreamException e) {
			e.printStackTrace();
		}
		return TextLength;
	}

	// **************
	// *
	// *Use adjectives and analyze emotions
	// *
	// **************
	//public static EmotionResult AssessEmotion(List<NERElement> AdjList, Integer sections) throws IOException {
	public static EmotionResult AssessEmotion(String text, Integer sections) throws IOException {
		String[] words= text.split("\\s+");
		List<EmotionElement> EmoLex;
		EmoLex = ReadLexicon();
		
		Integer Index; //Index der Emotion im Lexikon
		int ListPosition = 0; // Zählvariable für Emotionserkennung
		int DensityPosition = 0; // Zählvariable für Densityerkennung
		int EmotionAmount;
		double PpS = 100 / sections; // Percent per Section
		int PosSum = 0;
		int NegSum = 0;
		List<List<Double>> SectionEmotion = new ArrayList<>(); // Emotion Count for all Sections
		List<Double> SeEl = null;

		List<List<Double>> AllDensities = new ArrayList<>(); // Densities for all Sections
		List<Double> Density = null; // 16 Densities for one Section

		EmotionResult EmotionResult = new EmotionResult();
		for (int i = 0; i < sections; i++) { // Interation  über jede Textsektion
			SeEl = Arrays.asList(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
			while (ListPosition < words.length -1 && (ListPosition/(0.01*words.length)) < (i + 1) * PpS) {
				// solang nicht an alle Elemente aus AdjList und Prozentsatz des Textes pro
				// Sektion abgearbeitet
				Index = FindEqual(EmoLex, words[ListPosition]/*AdjList.get(ListPosition).getName()*/);
				if (Index != null) {
					SeEl = AddEmotion(EmoLex.get(Index), SeEl);
				}
				
				ListPosition++;
			}

			SectionEmotion.add(SeEl);
		}

		for (int i = 0; i < sections; i++) {
			Density = Arrays.asList(0.0);
			for (int j = 0; j < 1; j++) {
				EmotionAmount = 0;

				while (DensityPosition < /*AdjList.size()*/words.length -1
						&& /*AdjList.get(DensityPosition).getRelativePosition()*/(DensityPosition/(0.01*words.length)) < ((i) * PpS + (j + 1) * PpS / 1)) {

					// rel. Position des Adj. in gsm Text <(IndexSektion* 100%/SummeSektionen
					Index = FindEqual(EmoLex, words[DensityPosition]);
					if (Index != null) {
						EmotionAmount++;
					}

					DensityPosition++;
				}
				double D = EmotionAmount / (TextLength / (1D * sections));
				Density.set(j, D);
			}
			AllDensities.add(Density);
		}

		EmotionResult.setSectionEmotion(SectionEmotion);
		EmotionResult.setDensity(AllDensities);

		for (List<Double> Element : SectionEmotion) {
			PosSum = PosSum + Element.get(8).intValue();
			NegSum = NegSum + Element.get(9).intValue();
		}
		/*int  anger, anticipation,  disgust, fear, joy, sadness, surprise, trust;
		anger = anticipation =  disgust = fear = joy = sadness = surprise = trust = 0 ;
		for (List<Double> Element : SectionEmotion) {
			anger = anger + Element.get(0).intValue();
			anticipation = anticipation + Element.get(1).intValue();
			disgust = disgust + Element.get(2).intValue();
			fear = fear + Element.get(3).intValue();
			joy = joy + Element.get(4).intValue();
			sadness = sadness + Element.get(5).intValue();
			surprise = surprise + Element.get(6).intValue();
			trust = trust + Element.get(7).intValue();
		}
		System.out.println("Emotions:" + anger +"; " + anticipation+"; " +  disgust+"; " + fear+"; " + joy+"; " + sadness+"; " + surprise+"; " + trust+"; " + PosSum+"; " + NegSum);
*/
		EmotionResult.setNegSum(NegSum);
		EmotionResult.setPosSum(PosSum);
		return EmotionResult;

	}

	// *Create dictionary from NRC text file
	private static List<EmotionElement> ReadLexicon() throws IOException {
		String mFileLexicon = "data/lexica/NRC_emolex.txt";

		FileReader lexReader = new FileReader(mFileLexicon);
		BufferedReader bufferedLexReader = new BufferedReader(lexReader);
		List<EmotionElement> emotions = new ArrayList<EmotionElement>();
		String line;
		String[] words;
		EmotionElement EmEl = null;

		// Reading NRC lexicon
		while ((line = bufferedLexReader.readLine()) != null) {
			if (!line.isEmpty()) {
				words = line.split("\t");
				if (EmEl == null) {
					EmEl = new EmotionElement();
					EmEl.setName(words[0]);
				}

				if (!words[0].equals(EmEl.getName())) {
					emotions.add(EmEl);
					EmEl = new EmotionElement();
					EmEl.setName(words[0]);
				}

				if (Objects.equals(words[0], EmEl.getName()) && Objects.equals(words[2], "1")) {

					switch (words[1]) {
					case "anger":
						EmEl.setAnger(true);
						break;
					case "anticipation":
						EmEl.setAnticipation(true);
						break;
					case "disgust":
						EmEl.setDisgust(true);
						break;
					case "fear":
						EmEl.setFear(true);
						break;
					case "joy":
						EmEl.setJoy(true);
						break;
					case "sadness":
						EmEl.setSadness(true);
						break;
					case "surprise":
						EmEl.setSurprise(true);
						break;
					case "trust":
						EmEl.setTrust(true);
						break;
					case "positive":
						EmEl.setPositive(true);
						break;
					case "negative":
						EmEl.setNegative(true);
						break;
					}
				}
			}
		}

		// Close readers
		bufferedLexReader.close();
		lexReader.close();
		return emotions;
	}

	private static String txt2string(String SrcFileName) throws IOException {
		FileReader StoryReader = new FileReader(SrcFileName);
		BufferedReader bufferedStoryReader = new BufferedReader(StoryReader);
		String line;
		StringBuilder sb = new StringBuilder();

		while ((line = bufferedStoryReader.readLine()) != null) {
			if (!line.isEmpty()) {
				sb.append(line).append("\n");
			}
		}
		String story = sb.toString();
		bufferedStoryReader.close();
		StoryReader.close();
		return story;

	}

	private static Integer FindEqual(List<EmotionElement> EmoLex, String name) throws IOException {
		for (int i = 0; i < EmoLex.size(); i++) {
			if (EmoLex.get(i).getName().equals(name)) {
				Integer j = new Integer(i);
				return j;
			}

		}
		return null;

	}

	private static List<Double> AddEmotion(EmotionElement EmoLexEntry, List<Double> SeEl) {
		if (EmoLexEntry.getAnger()) {
			SeEl.set(0, SeEl.get(0) + 1);
		}
		if (EmoLexEntry.getAnticipation()) {
			SeEl.set(1, SeEl.get(1) + 1);
		}
		if (EmoLexEntry.getDisgust()) {
			SeEl.set(2, SeEl.get(2) + 1);
		}
		if (EmoLexEntry.getFear()) {
			SeEl.set(3, SeEl.get(3) + 1);
		}
		if (EmoLexEntry.getJoy()) {
			SeEl.set(4, SeEl.get(4) + 1);
		}
		if (EmoLexEntry.getSadness()) {
			SeEl.set(5, SeEl.get(5) + 1);
		}
		if (EmoLexEntry.getSurprise()) {
			SeEl.set(6, SeEl.get(6) + 1);
		}
		if (EmoLexEntry.getTrust()) {
			SeEl.set(7, SeEl.get(7) + 1);
		}
		if (EmoLexEntry.getPositive()) {
			SeEl.set(8, SeEl.get(8) + 1);
		}
		if (EmoLexEntry.getNegative()) {
			SeEl.set(9, SeEl.get(9) + 1);
			;
		}
		return SeEl;
	}

	public List<String> nameDetection() throws IOException {
		create_xml(mSrcFileName);
		analyze_xml(mSrcFileName, NNPList, TextLength);

		// Put the names to map
		Map<String, Integer> map = new HashMap<String, Integer>();
		for (NERElement nerElement : NNPList) {
			String name = nerElement.getName();
			if (map.containsKey(name)) {
				int count = map.get(name);
				map.put(name, count + 1);
			} else {
				map.put(name, 1);
			}
		}

		// Filter the map and delete names that occured less than 4 times
		map.entrySet().removeIf(entry -> entry.getValue() < 4);

		// Store the reduced names to a new list
		List<String> names = new ArrayList<String>(map.keySet());

		return names;
	}

	public EmotionResult main(String[] args) throws IOException {

		// Get time-wise starting point
		long startTime = System.nanoTime();
		
		String tempSrc = mSrcFileName;

		// prepareText(mSrcFileName, tempSrc);
		create_xml(tempSrc);
		// classify(tempSrc);
		TextLength = analyze_xml(tempSrc, NNPList, TextLength);

		String text = txt2string(mSrcFileName);
		EmotionResult EmotionResults = AssessEmotion(text, sections);
		EmotionResults.setNameList(NNPList);
		EmotionResults.printResult();
		
		// Get time-wise end point
		long endTime = System.nanoTime();
		// show total runtime in seconds
		System.out.println("Took " + (endTime - startTime) / 1000000000.0 + " seconds");
		TextLength = 0;

		try {
		File file = new File(mSrcFileName+".xml");
		file.delete();
		} catch (Exception e) {

			e.printStackTrace();
		}

		return EmotionResults;

	}

}