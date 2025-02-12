package org.aksw.horus.search.web;

import org.aksw.horus.search.query.MetaQuery;

/**
 * Created by dnes on 12/04/16.
 */
public class WebImageVO extends WebResourceVO {

    private String    fileName; //photo_name
    private String    filePath;  //photo_dir
    private WebSiteVO site; //photo_site_url
    private boolean detectedPerson = false;
    private boolean detectedOrganisation = false;
    private boolean detectedLocation = false;

    public WebImageVO(MetaQuery query, String imgURL, String websiteURL) {
        this.setQuery(query);
        this.setUrl(imgURL);
        this.site = new WebSiteVO(websiteURL);
    }

    public WebImageVO(){

    }

    public void setImageFilePath(String value){
        this.filePath = value;
    }
    public String getImageFilePath(){
        return this.filePath;
    }
    public void setImageFileName(String value){
        this.fileName = value;
    }
    public String getImageFileName(){
        return this.fileName;
    }

    public WebSiteVO getWebSite(){
        return this.site;
    }

    public void setWebSite(String websiteURL){
        this.site = new WebSiteVO(websiteURL);
    }

    public void setWebSite(WebSiteVO website){
        this.site = website;
    }

    public void setPersonDetected(boolean value){
        detectedPerson = value;
    }
    public void setOrganisationDetected(boolean value){
        detectedOrganisation = value;
    }
    public void setLocationDetected(boolean value){
        detectedLocation = value;
    }

    public boolean getPersonDetected(){
        return detectedPerson;
    }

    public boolean getOrganisationDetected(){
        return detectedOrganisation;
    }

    public boolean getLocationDetected(){
        return detectedLocation;
    }

}
