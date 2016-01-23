package com.elasticbox;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.io.IOException;
import java.util.List;

/**
 * Created by serna on 11/27/15.
 */
public interface APIClient {

    public JSONArray getBoxVersions(String boxId) throws IOException;

    public JSONArray getAllBoxes(String workspaceId) throws IOException;

    public JSONObject getBox(String boxId) throws IOException;


    public JSONObject getInstance(String instanceId) throws IOException;

    public JSONArray getInstances(String workspaceId, List<String> instanceIDs) throws IOException;

    public <T extends JSON> T doPost(String url, JSONObject resource) throws IOException;

}