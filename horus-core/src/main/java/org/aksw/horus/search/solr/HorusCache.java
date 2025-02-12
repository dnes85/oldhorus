package org.aksw.horus.search.solr;

import org.aksw.horus.Horus;
import org.aksw.horus.core.util.Constants;
import org.aksw.horus.search.cache.ICache;
import org.aksw.horus.search.query.MetaQuery;
import org.aksw.horus.search.result.DefaultSearchResult;
import org.aksw.horus.search.result.ISearchResult;
import org.aksw.horus.search.web.WebImageVO;
import org.aksw.horus.search.web.WebResourceVO;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.aksw.horus.core.util.Constants.*;

/**
 * Created by dnes on 01/06/16.
 */
public class HorusCache implements ICache<ISearchResult> {

    private SolrClient server;
    private static String SOLR_SERVER;
    private static final Logger LOGGER = LoggerFactory.getLogger(HorusCache.class);

    public HorusCache(){
        try{
            if ( Horus.HORUS_CONFIG != null ) {
                SOLR_SERVER = Horus.HORUS_CONFIG.getStringSetting("solr", "SERVER");
            }
            LOGGER.debug("Solr server: " + SOLR_SERVER);
            server = new HttpSolrClient(SOLR_SERVER);
        }catch (Exception e){
            LOGGER.error(e.toString());
        }
    }

    @Override
    public boolean contains(String identifier) {
        SolrDocumentList docList = null;
        try{
            SolrQuery query = new SolrQuery(LUCENE_SEARCH_RESULT_FIELD_QUERY_META + ":\"" + identifier + "\"").setRows(1);
            QueryResponse response = this.querySolrServer(query);
            docList = response.getResults();
        }catch (Exception e){
            LOGGER.error(e.toString());
        }
        return docList == null ? false : docList.size() > 0 ? true : false;
    }

    @Override
    public ISearchResult getEntry(String metaQueryStr) {

        List<WebResourceVO> resources = new ArrayList<WebResourceVO>();
        MetaQuery metaQuery = null;
        Long hitCount = 0L;

        try {

            /*
            SolrQuery query = new SolrQuery(
                    Constants.LUCENE_SEARCH_RESULT_QUERY_FIELD + ":\"" + identifier + "\"" + " AND " +
                    Constants.LUCENE_SEARCH_RESULT_QUERY_FIELD + ":\"" + identifier + "\"" + " AND " +
                    Constants.LUCENE_SEARCH_RESULT_QUERY_FIELD + ":\"" + identifier + "\"" + " AND " +
                    Constants.LUCENE_SEARCH_RESULT_QUERY_FIELD + ":\"" + identifier + "\""
            ).setRows(50);
            */
            SolrQuery query = new SolrQuery(LUCENE_SEARCH_RESULT_FIELD_QUERY_META + ":\"" + metaQueryStr + "\"").setRows(200);

            query.addField(LUCENE_SEARCH_RESULT_FIELD_ID);
            query.addField(LUCENE_SEARCH_RESULT_FIELD_QUERY_META);
            query.addField(LUCENE_SEARCH_RESULT_FIELD_CREATED);
            query.addField(LUCENE_SEARCH_RESULT_FIELD_QUERY_TEXT);
            query.addField(LUCENE_SEARCH_RESULT_FIELD_QUERY_ADDITIONAL_CONTENT);
            query.addField(LUCENE_SEARCH_RESULT_FIELD_QUERY_NER_TYPE);
            query.addField(LUCENE_SEARCH_RESULT_FIELD_QUERY_SEARCH_ENGINE_FEATURE);
            query.addField(LUCENE_SEARCH_RESULT_FIELD_QUERY_HIT_COUNT);
            query.addField(LUCENE_SEARCH_RESULT_FIELD_RESOURCE_TITLE);
            query.addField(LUCENE_SEARCH_RESULT_FIELD_RESOURCE_URL);
            query.addField(LUCENE_SEARCH_RESULT_FIELD_RESOURCE_SEARCH_RANK);
            query.addField(LUCENE_SEARCH_RESULT_FIELD_RESOURCE_LANGUAGE);
            query.addField(LUCENE_SEARCH_RESULT_FIELD_IMAGE_NAME);
            query.addField(LUCENE_SEARCH_RESULT_FIELD_IMAGE_PATH);
            query.addField(LUCENE_SEARCH_RESULT_FIELD_SITE_CONTENT);
            query.addField(LUCENE_SEARCH_RESULT_FIELD_SITE_PAGE_RANK);

            QueryResponse response = this.querySolrServer(query);

            if (response.getResults().size() > 0){

                //String metaQueryStr = (String) response.getResults().get(0).get(LUCENE_SEARCH_RESULT_FIELD_QUERY_META);
                //Integer idTerm = Integer.valueOf(metaQueryStr.split(METAQUERY_SEPARATOR)[4]);

                metaQuery = new MetaQuery(metaQueryStr);

                for (SolrDocument doc : response.getResults()) {

                    hitCount = (Long) doc.get(Constants.LUCENE_SEARCH_RESULT_FIELD_QUERY_HIT_COUNT);

                    if (!((String) doc.get(LUCENE_SEARCH_RESULT_FIELD_RESOURCE_URL)).isEmpty()) {

                        WebImageVO img = new WebImageVO();

                        img.setCachedID((String) doc.get(LUCENE_SEARCH_RESULT_FIELD_ID));

                        img.setTitle((String) doc.get(LUCENE_SEARCH_RESULT_FIELD_RESOURCE_TITLE));
                        img.setUrl((String) doc.get(LUCENE_SEARCH_RESULT_FIELD_RESOURCE_URL));
                        img.setSearchRank(((Long) doc.get(LUCENE_SEARCH_RESULT_FIELD_RESOURCE_SEARCH_RANK)).intValue());
                        img.setCached(true);
                        img.setQuery(metaQuery);
                        img.setLanguage((String) doc.get(LUCENE_SEARCH_RESULT_FIELD_RESOURCE_LANGUAGE));
                        img.setTotalHitCount(((Long) doc.get(LUCENE_SEARCH_RESULT_FIELD_QUERY_HIT_COUNT)).intValue());

                        img.setImageFileName((String)doc.get(LUCENE_SEARCH_RESULT_FIELD_IMAGE_NAME));
                        img.setImageFilePath((String)doc.get(LUCENE_SEARCH_RESULT_FIELD_IMAGE_PATH));
                        //img.setWebSite((String)doc.get(LUCENE_SEARCH_RESULT_URL_FIELD));

                        resources.add(img);
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.error(e.toString());
        }

        return new DefaultSearchResult(resources, hitCount, metaQuery, true);

    }

    @Override
    public ISearchResult removeEntryByPrimaryKey(String primaryKey) {
        throw new RuntimeException("not yet implemented");
    }

    @Override
    public boolean updateEntry(ISearchResult object) {
        throw new RuntimeException("not yet implemented");
    }

    @Override
    public List<ISearchResult> addAll(List<ISearchResult> listToAdd) {
        for (ISearchResult result : listToAdd ) this.add(result);
        try {
            this.server.commit();
        }
        catch (java.io.CharConversionException e ) {
            e.printStackTrace();
        }
        catch (SolrServerException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return listToAdd;
    }

    @Override
    public ISearchResult add(ISearchResult entry) {

        try {
            if (!entry.isCached()) {
                this.server.add(searchResultToDocument(entry));
                LOGGER.info(String.format("Query: '%s' was not found in the cache, starting to query!", entry.getQuery()));
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return entry;
    }

    private List<SolrInputDocument> searchResultToDocument(ISearchResult entry) {

        List<SolrInputDocument> documents = new ArrayList<SolrInputDocument>();

        if (entry.getWebResources().isEmpty()) {

            SolrInputDocument solrDocument = new SolrInputDocument();
            solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_QUERY_META, entry.getQuery().toString());
            solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_CREATED, new Date().getTime());
            solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_QUERY_TEXT, entry.getQuery().getText());
            solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_QUERY_ADDITIONAL_CONTENT, entry.getQuery().getAdditionalContent() );
            solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_QUERY_NER_TYPE, entry.getQuery().getType().toString());
            solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_QUERY_SEARCH_ENGINE_FEATURE,  entry.getQuery().getSearchEngineFeature());
            solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_QUERY_HIT_COUNT, entry.getTotalHitCount());
            solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_RESOURCE_TITLE, "");
            solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_RESOURCE_URL, "");
            solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_RESOURCE_SEARCH_RANK, -1);
            solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_RESOURCE_LANGUAGE, "");
            solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_IMAGE_NAME, "");
            solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_IMAGE_PATH, "");
            solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_SITE_CONTENT, "");
            solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_SITE_PAGE_RANK, -1);
            documents.add(solrDocument);
        }
        else {

            for (WebResourceVO resource : entry.getWebResources()) {

                WebImageVO image = (WebImageVO) resource;
                SolrInputDocument solrDocument = new SolrInputDocument();
                solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_QUERY_META, entry.getQuery().toString());
                solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_CREATED, new Date().getTime());
                solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_QUERY_TEXT, entry.getQuery().getText());
                solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_QUERY_ADDITIONAL_CONTENT, entry.getQuery().getAdditionalContent() );
                solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_QUERY_NER_TYPE, entry.getQuery().getType().toString());
                solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_QUERY_SEARCH_ENGINE_FEATURE,  entry.getQuery().getSearchEngineFeature());
                solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_QUERY_HIT_COUNT, entry.getTotalHitCount());
                solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_RESOURCE_TITLE, image.getTitle());
                solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_RESOURCE_URL, image.getUrl());
                solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_RESOURCE_SEARCH_RANK, image.getSearchRank());
                solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_RESOURCE_LANGUAGE, image.getLanguage());
                solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_IMAGE_NAME, image.getImageFileName());
                solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_IMAGE_PATH, image.getImageFilePath());
                solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_SITE_CONTENT, image.getWebSite().getText());
                solrDocument.addField(LUCENE_SEARCH_RESULT_FIELD_SITE_PAGE_RANK, image.getWebSite().getPageRank());

                documents.add(solrDocument);
            }
        }

        return documents;
    }

    private QueryResponse querySolrServer(SolrQuery query) throws Exception {
        try {

            return this.server.query(query);
        }
        catch (SolrServerException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }


}
