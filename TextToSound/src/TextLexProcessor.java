import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextLexProcessor {
	
	// --------------------------------------------------------
	// Members
	// --------------------------------------------------------
	
	private String mSrcFileName;
	private String mFileLexicon;
	private List<String> mLex;
	private StringBuilder mText = new StringBuilder();
	
	
	// --------------------------------------------------------
	// Constructors
	// --------------------------------------------------------
	
	public TextLexProcessor() {};
	/**
	 * Constructor.
	 * @param src source text to be analysed
	 * @param lex lexicon to use
	 */
	public TextLexProcessor(String src, String lex) {
		mSrcFileName = src;
		mFileLexicon = lex;
	};
	
	
	// --------------------------------------------------------
	// Text-processing methods
	// --------------------------------------------------------
	
	/**
	 * Process the given source text and analyze occurrence of tokens
	 * @throws IOException
	 */
	public ProcessedResult process() throws IOException {
		
		verifySrc();				// Verify source file. Prints error message if needed.
		mLex = readLexicon();		// Read tokens from lexicon and store in list
		
		// Initialize variables, read input file
		String line;
		int numLine = 0, numMatch = 0, textLength = 0;
		Map<String, TargetInfo> result = new HashMap<String, TargetInfo>();
		Scanner scanner = new Scanner(new File(mSrcFileName), "UTF-8");
		scanner.useDelimiter("\n");
		
		// Process file line by line
		while(scanner.hasNextLine()){
			line = scanner.nextLine();
			// Remove all characters that are not alphabets
			String text = line.replaceAll("[^a-zA-Z ]", "").toLowerCase();
			mText.append(text);
			// Find all target token occurrences in line
			for (String lexLine : mLex) {
				// Check lexicon dimension
				String[] lineContent = lexLine.split(",");
				String token = lineContent[0];
				// Setting word boundaries to find matches of the whole word
				String pattern = "(?<!\\S)" + Pattern.quote(token) + "(?!\\S)";
				Matcher m = Pattern.compile(pattern).matcher(text);
				while (m.find()) {
					int matchPos = m.start() + textLength;
					if (lineContent.length == 3) {
						int phys = Integer.parseInt(lineContent[1]);
						boolean character = Boolean.parseBoolean(lineContent[2]);
						pushAttributes(result, token, phys, character, matchPos);
					} else {
						pushMatchPos(result, token, matchPos);
					}
				    numMatch++;
				}
			}
			textLength += text.length();
			numLine++;
		}
		scanner.close();
		updateAnalysisComputation(result, textLength);
		
		ProcessedResult tokenAnalysis = 
				new ProcessedResult(textLength, numLine, result);
		tokenAnalysis.printRes();
		return tokenAnalysis; 
	}
	
	/**
	 * Read the lexicon for further text analysis which
	 * identifies the occurrence of tokens
	 * @return A list of token targets
	 * @throws IOException
	 */
	private List<String> readLexicon() throws IOException {
		
		// Preparing to read target lexicon
		FileReader lexReader = new FileReader(mFileLexicon);
		BufferedReader bufferedLexReader = new BufferedReader(lexReader);
		List<String> tokens = new ArrayList<String>();
		String line;
		
		// Reading lexicon
		while ((line = bufferedLexReader.readLine())!= null) {
			if (!line.isEmpty()) {
				tokens.add(line);
			}
		}
		
		// Close readers
		bufferedLexReader.close();
		lexReader.close();
		return tokens;
	}
	
	/**
	 * Verify source file. Check if the file is given or if the path exists.
	 * Prints error message if needed.
	 */
	private void verifySrc() {
		// Check if a source file is given
		if(mSrcFileName == null){
			System.out.println("Error: file missing!");
			System.exit(1);
		}
		// Check the path of the file
		else {
			Path path = Paths.get(mSrcFileName);
			if (!Files.exists(path)) {
				System.out.println("Error: file path doesn't exist!");
				System.exit(1);
			}
		}
	}
	
	/**
	 * Create new found target in the result map, initialize attributes
	 * and push the first match position.
	 * @param result result map
	 * @param target target string
	 * @param phys Target physical size or age from the scale of 0-10
	 * @param character target character good(0) or evil (1)
	 * @param matchPos match position
	 */
	private void pushAttributes(Map<String, TargetInfo> result, 
			String target, int phys, boolean character, int matchPos) {
		
		TargetInfo targetInfo;
		if (result.containsKey(target)) {
			targetInfo = result.get(target);
		} else {
			targetInfo = new TargetInfo(target);
		}
		targetInfo.setTargetPhys(phys);
		targetInfo.setTargetCharacter(character);
		targetInfo.pushOccurenceIndexes(matchPos);
		result.put(target, targetInfo);
	}
	
	/**
	 * Push newly found match position to result map.
	 * @param result result map
	 * @param target target string
	 * @param matchPos match position
	 */
	private void pushMatchPos(Map<String, TargetInfo> result, 
			String target, int matchPos) {
		
		TargetInfo targetInfo;
		if (result.containsKey(target)) {
			targetInfo = result.get(target);
		} else {
			targetInfo = new TargetInfo(target);
		}
		targetInfo.pushOccurenceIndexes(matchPos);
		result.put(target, targetInfo);
	}
	
	/**
	 * Update other information of the TargetOccurence class, including
	 * the percentage expression of where the match is from the whole text
	 * for the target and total occurences.
	 * @param result
	 * @param textLength
	 */
	public void updateAnalysisComputation(
			Map<String, TargetInfo> result, int textLength) {
		for (Map.Entry<String, TargetInfo> entry : result.entrySet()) {
			entry.getValue().calcRelativOccPos(textLength);
			entry.getValue().calcTotalOcc();
		}
	}
	
	/**
	 * Find target death by checking for death-indicating words between
	 * 100 characters around the last match position. 
	 * <br>
	 * This method should be carried out only after the occurrence results are aquired.
	 * @param res The ProcessResult returned after the text has been processed to find targets
	 * @throws FileNotFoundException
	 */
	public void findDeaths(ProcessedResult res) throws FileNotFoundException {
		String[] deathIndicators = {"dead","die","dies","died","death","killed"};		
		
		for (Map.Entry<String, TargetInfo> entry : res.getOccurenceInfos().entrySet()) {
			String target = entry.getKey();
			TargetInfo targetInfo = entry.getValue();
			int lastOccPos = targetInfo.getOccurenceIndexes().get(targetInfo.getNumTotalOcc()-1);
			String subString = mText.substring(lastOccPos-25, lastOccPos+25);
			//System.out.println(subString);
			
			for (String word : deathIndicators) {
				if (subString.indexOf(word) > 0) {
					System.out.println(target + " Death!");
				}
			}
		}
		
	}
	
	public static void main(String[] args) throws IOException {
		TextLexProcessor processor1 = new TextLexProcessor("data/the-happy-prince.txt", "data/lexicon_animals.csv");
//		TextLexProcessor processor1 = new TextLexProcessor("data/the-fox-and-the-crow.txt", "data/lexicon_animals.csv");
		ProcessedResult result = processor1.process();
		processor1.findDeaths(result);
		//System.out.println(processor1.mText.toString());
	}
	
}