/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.    See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.    You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.acmerecommender;

import com.google.gson.Gson;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;

/**
 * A servlet which returns recommendations. The servlet accepts GET and POST
 * HTTP requests, and looks for the following parameters:
 *
 * api_key: of the site
 * userID: the user ID for which to produce recommendations
 * howMany: the number of recommendations to produce
 * itemID: (optional) the item ID of the item user interacted with
 * action: (optional) the interaction
 * format: (optional) the format of the response
 * debug: (optional) output a lot of information that is useful in debugging. Defaults to false.
 *
 * <li><em>userID</em>: the user ID for which to produce recommendations</li>
 * <li><em>howMany</em>: the number of recommendations to produce</li>
 * <li><em>debug</em>: (optional) output a lot of information that is useful in debugging.
 * Defaults to false, of course.</li>
 *
 *
 * <p>The response is text, xml, json or base64 encoded json, and contains a list of the IDs of recommended items, in descending
 * order of relevance, one per line.</p>
 */
public final class RecommenderServlet extends HttpServlet {

    private static final int NUM_TOP_PREFERENCES = 10;
    private static final int DEFAULT_HOW_MANY = 10;
    private static final Logger log = LoggerFactory.getLogger(RecommenderServlet.class);
    private Recommender recommender;
    private RecommenderFactory recFactory;
    private HashMap<Integer,RecommenderFactory> recommenderFactoryMap = new HashMap<>();

    @Override
    public void init(ServletConfig config) throws ServletException {
        log.info("Initializing recommender servlet!!");
        super.init(config);
        Database db = new Database();
        ArrayList<Integer> ids = db.userAccountIDs();

        for (Integer id : ids) {
            RecommenderFactory r = new RecommenderFactory( id);
            recommenderFactoryMap.put( id, r);
        }
        log.info("Finished initializing recommender!!");
    }

    @Override
    public void doGet(HttpServletRequest request,
                                        HttpServletResponse response) throws ServletException {

        String userIDString = request.getParameter("userID");
        String api_key = request.getParameter("api_key");
        String itemIDString = request.getParameter("itemID");
        String action = request.getParameter("action");
        String host = request.getRemoteHost();
        String howManyString = request.getParameter("howMany");
        boolean debug = Boolean.parseBoolean(request.getParameter("debug"));
        String format = request.getParameter("format");
        if (format == null) {
            format = "json";
        }

        HashMap<String, Object> result = new HashMap<>();

        Validator val = new Validator( host, userIDString, api_key, itemIDString, action);
        result.put( "status", val.getStatus());
        result.put( "message", val.getMessage());

        if( val.getValid()){
            long userID = Long.parseLong(userIDString);
            int howMany = howManyString == null ? DEFAULT_HOW_MANY : Integer.parseInt(howManyString);

            try {
                Database db = new Database();
                int userAccountID = db.getUserAccountID(api_key);
                recFactory = recommenderFactoryMap.get(userAccountID);
                if( recFactory == null){
                    recFactory = new RecommenderFactory( userAccountID);
                    recommenderFactoryMap.put( userAccountID, recFactory);
                }

                if( !itemIDString.isEmpty() && !action.isEmpty()){
                    long itemID = Long.parseLong(itemIDString);
                    float preference = db.Store(api_key, userID, itemID, action);
                    if( preference > 0 ){
                        recFactory.getDataModel().setPreference( userID , itemID, preference);
                    }
                }

                List<RecommendedItem> items = recFactory.recommend(userID, howMany);
                result.put( "items", items);

                if ("text".equals(format)) {
                    writePlainText(response, userID, debug, items);
                } else if ("xml".equals(format)) {
                    writeXML(response, items);
                }

            } catch (TasteException | IOException te) {
                throw new ServletException(te);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                e.printStackTrace();
            }
        }

        try {
            if ("json".equals(format)) {
                writeJSON(response, result);
            } else if ("base64".equals(format)) {
                writeBase64(response, result);
            } else if( !"text".equals(format) && !"xml".equals(format)){
                throw new ServletException("Bad format parameter: " + format);
            }
        }
        catch(IOException e){
            e.printStackTrace();
        }
    }

    private static void writeXML(HttpServletResponse response, Iterable<RecommendedItem> items) throws IOException {
        response.setContentType("application/xml");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        PrintWriter writer = response.getWriter();
        writer.print("<?xml version=\"1.0\" encoding=\"UTF-8\"?><recommendedItems>");
        for (RecommendedItem recommendedItem : items) {
            writer.print("<item><value>");
            writer.print(recommendedItem.getValue());
            writer.print("</value><id>");
            writer.print(recommendedItem.getItemID());
            writer.print("</id></item>");
        }
        writer.println("</recommendedItems>");
    }

    private static void writeJSON(HttpServletResponse response, HashMap result) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        String json = new Gson().toJson(result );
        response.getWriter().write(json);
    }

    private static void writeBase64(HttpServletResponse response, HashMap result) throws IOException {
        response.setContentType("image/jpeg");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        String json = new Gson().toJson(result );
        byte[] encodedBytes = Base64.getEncoder().encode(json.getBytes());
        response.getWriter().write(new String(encodedBytes));
    }

    private void writePlainText(HttpServletResponse response,
                                                            long userID,
                                                            boolean debug,
                                                            Iterable<RecommendedItem> items) throws IOException, TasteException {
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        PrintWriter writer = response.getWriter();
        if (debug) {
            writeDebugRecommendations(userID, items, writer);
        } else {
            writeRecommendations(items, writer);
        }
    }

    private static void writeRecommendations(Iterable<RecommendedItem> items, PrintWriter writer) {
        writer.print(MethodHandles.lookup().lookupClass() + "\n");
        for (RecommendedItem recommendedItem : items) {
            writer.print(recommendedItem.getValue());
            writer.print('\t');
            writer.println(recommendedItem.getItemID());
        }
    }

    private void writeDebugRecommendations(long userID, Iterable<RecommendedItem> items, PrintWriter writer)
                    throws TasteException {
        DataModel dataModel = recFactory.getDataModel();
        writer.print("User:");
        writer.println(userID);
        writer.print("Recommender: ");
        writer.println(recommender);
        writer.println();
        writer.print("Top ");
        writer.print(NUM_TOP_PREFERENCES);
        writer.println(" Preferences:");
        PreferenceArray rawPrefs = dataModel.getPreferencesFromUser(userID);
        int length = rawPrefs.length();
        PreferenceArray sortedPrefs = rawPrefs.clone();
        sortedPrefs.sortByValueReversed();
        // Cap this at NUM_TOP_PREFERENCES just to be brief
        int max = Math.min(NUM_TOP_PREFERENCES, length);
        for (int i = 0; i < max; i++) {
            Preference pref = sortedPrefs.get(i);
            writer.print(pref.getValue());
            writer.print('\t');
            writer.println(pref.getItemID());
        }
        writer.println();
        writer.println("Recommendations:");
        for (RecommendedItem recommendedItem : items) {
            writer.print(recommendedItem.getValue());
            writer.print('\t');
            writer.println(recommendedItem.getItemID());
        }
    }

    @Override
    public void doPost(HttpServletRequest request,
                                         HttpServletResponse response) throws ServletException {
        doGet(request, response);
    }

    @Override
    public String toString() {
        return "RecommenderServlet[recommender:" + recommender + ']';
    }
}
