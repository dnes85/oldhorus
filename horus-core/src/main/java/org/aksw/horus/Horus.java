package org.aksw.horus;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import org.aksw.horus.algorithm.FaceDetectOpenCV;
import org.aksw.horus.core.util.Global;
import org.aksw.horus.core.util.TimeUtil;
import org.aksw.horus.search.HorusEvidence;
import org.aksw.horus.search.crawl.ResourceExtractor;
import org.aksw.horus.search.query.MetaQuery;
import org.aksw.horus.search.web.ISearchEngine;
import org.aksw.horus.search.web.WebImageVO;
import org.aksw.horus.search.web.WebResourceVO;
import org.aksw.horus.search.web.bing.AzureBingSearchEngine;
import org.ini4j.Ini;
import org.ini4j.InvalidFileFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Created by dnes on 12/04/16.
 */
public abstract class Horus {

    private static List<HorusSentence>  horusSentences = new ArrayList<>();
    public  static HorusConfig          HORUS_CONFIG;
    private static final Logger         LOGGER             = LoggerFactory.getLogger(Horus.class);

    private static double               PER_THRESHOLD;
    private static double               LOC_THRESHOLD;
    private static double               ORG_THRESHOLD;
    private static int                  PER_OFFSET;
    private static int                  LOC_OFFSET;
    private static int                  ORG_OFFSET;

    /***
     * annotate a given input text with Stanford POS tagger
     * @param text
     * @throws Exception
     */
    private static void annotateWithStanford(String text) throws Exception{

        LOGGER.debug("starting annotation with Stanford POS");

        try {

            int iSentence = 0;
            int iTerm = 0;
            int iPosition = 0;

            Properties props = new Properties();
            props.setProperty("annotators","tokenize, ssplit, pos, depparse");

            StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
            Annotation annotation = new Annotation(text);
            pipeline.annotate(annotation);
            List<CoreMap> sentences = annotation.get(CoreAnnotations.SentencesAnnotation.class);
            for (CoreMap sentence : sentences) {
                HorusSentence sent = new HorusSentence(iSentence, sentence.toString());
                for (CoreLabel token: sentence.get(CoreAnnotations.TokensAnnotation.class)) {
                    String word = token.get(CoreAnnotations.TextAnnotation.class);
                    String pos = token.get(CoreAnnotations.PartOfSpeechAnnotation.class);
                    String ne = token.get(CoreAnnotations.NamedEntityTagAnnotation.class);

                    //Object dep = token.get(CoreAnnotations.DependentsAnnotation.class);
                    //adding all isolated tokens as terms
                    HorusToken tt = new HorusToken(iTerm, word, pos, Global.NLPToolkit.STANFORD, iPosition, ne);
                    sent.addToken(tt);
                    iTerm++; iPosition++;
                }

                //is there compound at this sentence?
                SemanticGraph dependencies1 =
                        sentence.get(SemanticGraphCoreAnnotations.
                                CollapsedCCProcessedDependenciesAnnotation.class);

                //todo: check performance constraints
                if (dependencies1.toList().contains("compound(")){

                    String[] depArray = dependencies1.toList().split("\n");
                    for (int i=0;i<depArray.length;i++){
                        if (depArray[i].contains("compound(")){
                            String[] coumpoundStr =
                                    depArray[i].substring(depArray[i].indexOf("(") + 1, depArray[i].length()-1).split(",");

                            Integer first = Math.min(Integer.valueOf(coumpoundStr[0].split("-")[1]),
                                    Integer.valueOf(coumpoundStr[1].split("-")[1]));

                            Integer second = Math.max(Integer.valueOf(coumpoundStr[0].split("-")[1]),
                                    Integer.valueOf(coumpoundStr[1].split("-")[1]));


                            HorusToken token = sent.getToken(first - 1);
                            token.setRefNextToken(second - 1);

                        }
                    }

                }

                horusSentences.add(sent);
                iSentence++; iTerm = 0;
            }

            LOGGER.debug("done!");

        }catch(Exception e){
            LOGGER.error(e.toString());
        }
    }

    /*
    private static void cacheTerm(String term) throws Exception {

        LOGGER.debug(":: caching [" + term);
        AzureBingSearchEngine engine = new AzureBingSearchEngine();
        //engine.downloadAndCacheImages();

    }
    */

    private static List<MetaQuery>  setSearchEngineQueries(){

        List<MetaQuery> queries = new ArrayList<>();

        //filtering out and creating linked list of terms
        int termID = 0;
        for ( HorusSentence sentence : horusSentences) {
            for (HorusToken token: sentence.getTokens()){

                HorusTerm term = null;
                //terms as tokens
                    if (token.getPOS(Global.NLPToolkit.STANFORD).equals("NN") || token.getPOS(Global.NLPToolkit.STANFORD).equals("NNS") ||
                            token.getPOS(Global.NLPToolkit.STANFORD).equals("NNP") || token.getPOS(Global.NLPToolkit.STANFORD).equals("NNPS")) {

                        //converts tokens to terms
                        term = new HorusTerm(termID);

                        if (!token.isComposed()) {

                            term.addToken(token);

                            queries.add(new MetaQuery(Global.NERType.LOC, term.getTokensValue(), "", term.getId()));
                            queries.add(new MetaQuery(Global.NERType.PER, term.getTokensValue(), "", term.getId()));
                            queries.add(new MetaQuery(Global.NERType.ORG, term.getTokensValue(), "", term.getId()));
                        }
                        else { //composed term

                            if (token.getRefPrevToken()!= 0)
                                term.addToken(sentence.getToken(token.getRefPrevToken()));

                            term.addToken(sentence.getToken(token.getIndex()));

                            if (token.getRefNextToken()!= 0)
                                term.addToken(sentence.getToken(token.getRefNextToken()));

                            queries.add(new MetaQuery(Global.NERType.LOC, term.getTokensValue(), "", term.getId()));
                            queries.add(new MetaQuery(Global.NERType.PER, term.getTokensValue(), "", term.getId()));
                            queries.add(new MetaQuery(Global.NERType.ORG, term.getTokensValue(), "", term.getId()));
                        }

                        sentence.addTerm(term);
                        termID++;
                    }
            }
        }

        return queries;
    }
    // *************************************** public methods ***************************************

    public static List<HorusSentence> process(String text) throws Exception{
        LOGGER.info(":: Processing...");

        long start = System.currentTimeMillis();

        /* 1. Annotate text */
        annotateWithStanford(text);

        /* 2. Creating Search Queries */
        List<MetaQuery> queries = setSearchEngineQueries();

        /* 3. Querying and Caching */
        if ( queries.size() <= 0 ) {

            LOGGER.warn("-> none query has been generated for this input! there's nothing to do ...");

        } else {

            LOGGER.info("-> preparing queries took " + TimeUtil.formatTime(System.currentTimeMillis() - start));

            ISearchEngine engine = new AzureBingSearchEngine();
            long startCrawl = System.currentTimeMillis();

            ResourceExtractor ext = new ResourceExtractor(queries);
            List<HorusEvidence> evidencesPerQuery = ext.extractAndCache(engine);
            LOGGER.info(" -> extracting evidences took " + TimeUtil.formatTime(System.currentTimeMillis() - startCrawl));

            /* 4. Running models */
            recognizeEntities(evidencesPerQuery);

            /* 5. based on indicators, make the decision */
            makeDecisionAmongAll();

        }

        /* 5. return the containers */
        return horusSentences;
    }
    /***
     * init method
     */
    public static void init(){

        try {

            if ( Horus.HORUS_CONFIG  == null )
                Horus.HORUS_CONFIG = new HorusConfig(new Ini(new File(Horus.class.getResource("/horus.ini").getFile())));

            PER_THRESHOLD = Horus.HORUS_CONFIG.getDoubleSetting("crawl", "PER_THRESHOLD");
            ORG_THRESHOLD = Horus.HORUS_CONFIG.getDoubleSetting("crawl", "ORG_THRESHOLD");
            LOC_THRESHOLD = Horus.HORUS_CONFIG.getDoubleSetting("crawl", "LOC_THRESHOLD");

            PER_OFFSET = Horus.HORUS_CONFIG.getIntegerSetting("crawl", "PER_OFFSET");
            ORG_OFFSET = Horus.HORUS_CONFIG.getIntegerSetting("crawl", "ORG_OFFSET");
            LOC_OFFSET = Horus.HORUS_CONFIG.getIntegerSetting("crawl", "LOC_OFFSET");


        } catch (InvalidFileFormatException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /***
     * print the results (stdout)
     */
    public static void printResults(){
        LOGGER.info(":: Printing results...");
        DecimalFormat f = new DecimalFormat("0.0000");

        for (HorusSentence s : horusSentences) {
            LOGGER.info(":: Sentence Index " + s.getSentenceIndex() + ": " + s.getSentenceText());
            for (HorusToken tk : s.getTokens()) {

                LOGGER.info("  -- token : " + tk.getIndex() + "-" + tk.getTokenValue());
                HorusTerm t = s.getTermByTokenId(tk.getIndex());

                if (t != null && !tk.isComposed()){
                    LOGGER.info("    -- term index   : " + t.getId());
                    LOGGER.info("    -- POS       : " + tk.getPOS(Global.NLPToolkit.STANFORD));
                    LOGGER.info("    -- P(PER)       : " + f.format(t.getProbability(Global.NERType.PER).doubleValue()));
                    LOGGER.info("    -- P(LOC)       : " + f.format(t.getProbability(Global.NERType.LOC).doubleValue()));
                    LOGGER.info("    -- P(ORG)       : " + f.format(t.getProbability(Global.NERType.ORG).doubleValue()));
                    LOGGER.info("    -- Stanford NER : " + tk.getNER(Global.NLPToolkit.STANFORD));
                    LOGGER.info("       -- HORUS NER    : " + t.getHorusNER());
                    LOGGER.info("");
                }
            }

            LOGGER.info("  ** extra analysis: compounds ** ");
            LOGGER.info("");

            for (HorusTerm t : s.getTerms()) {
                if (t.isComposedTerm()) {
                    LOGGER.info("  -- term index  : " + t.getId());
                    LOGGER.info("  -- tokens      : " + t.getTokensValue());
                    LOGGER.info("  -- POS      : " + t.getTokensPOS(Global.NLPToolkit.STANFORD));
                    LOGGER.info("  -- P(PER)      : " + f.format(t.getProbability(Global.NERType.PER)));
                    LOGGER.info("  -- P(LOC)      : " + f.format(t.getProbability(Global.NERType.LOC)));
                    LOGGER.info("  -- P(ORG)      : " + f.format(t.getProbability(Global.NERType.ORG)));
                    LOGGER.info("     -- HORUS NER   : " + t.getHorusNER());
                    LOGGER.info("");
                }
            }
            LOGGER.info("");
        }
    }

    /***
     * exports the metadata of execution set to the MEX interchange file format
     * @param s1
     * @param s2
     * @param s3
     * @param s4
     */
    public static void exportToMEX(String s1, String s2, String s3, String s4){
        LOGGER.info(":: Exporting horus metadata (MEX)");

        //TODO: integrate LOG4MEX and convert container to mex
    }

    /***
     * exports the metadata of execution set to the MEX interchange file format
     * @param s1
     * @param s2
     * @param s3
     * @param s4
     */
    public static void exportToNIF(String s1, String s2, String s3, String s4){
        LOGGER.info(":: Exporting horus metadata (NIF)");

        //TODO: integrate ? and convert container to nif
    }


    private static void recognizeEntities(List<HorusEvidence> evidences) throws Exception{
        LOGGER.info(":: Recognizing Entities - start");

        //for ( Map.Entry<MetaQuery, HorusEvidence> evidencesToPosition : evidences.entrySet()) {

        //updating the evidences per terms
        for (HorusEvidence e: evidences) {
             HorusTerm t = getTermByPosition(e.getQuery().getHorusTermIdentifier());
             t.setEvidence(e.getQuery().getType(), e);
        }


        //reading the updated sentence and detecting objects
        for (HorusSentence h : horusSentences) {
            LOGGER.debug(":: Sentence Index " + h.getSentenceIndex() + ": " + h.getSentenceText());
            for (HorusTerm t : h.getTerms()) {
                LOGGER.debug(":: is person? " + t.getTokensValue());
                setPersonDetected(t);
                LOGGER.debug(":: is organisation? " + t.getTokensValue());
                setOrganisationDetected(t);
                LOGGER.debug(":: is location? " + t.getTokensValue());
                setLocationDetected(t);
            }
        }

        LOGGER.info(":: Recognizing Entities - done");

    }


    //TODO: to train J48 here...
    private static void makeDecisionAmongAll() throws Exception{

    }

    //TODO: create a singleton later...
    private static void setPersonDetected(HorusTerm t) throws Exception{
        HorusEvidence e = t.getEvidences(Global.NERType.PER);
        int count = 0;
        int match = 0;
        double prob = 0d;
        FaceDetectOpenCV fd = new FaceDetectOpenCV();
        for (WebResourceVO r: e.getResources()){
            WebImageVO img = (WebImageVO) r;
            if (!img.getImageFileName().equals("err.err")) {
                boolean ret = fd.faceDetected(new File(img.getImageFilePath() + img.getImageFileName()));
                img.setPersonDetected(ret);
                count++;
                if (ret) match++;
            }
        }
        if (count!=0)
            prob = Double.valueOf(match) / Double.valueOf(count);

        t.setProbability(Global.NERType.PER, prob);
    }
    //todo: to implement!
    private static void setLocationDetected(HorusTerm t) throws Exception{
        t.setProbability(Global.NERType.LOC, 0.0d);
    }
    //todo: to implement!
    private static void setOrganisationDetected(HorusTerm t) throws Exception{
        t.setProbability(Global.NERType.ORG, 0.0d);
    }

    private static HorusTerm getTermByPosition(int position) throws Exception{
        int aux = 0;
        for (HorusSentence h : horusSentences) {
            aux += h.getTerms().size();
            if (aux >= position){
                for (HorusTerm t :  h.getTerms()) {
                    if (t.getId() == position){
                        return t;
                    }
                }
            }
        }
        return null;
    }

}
