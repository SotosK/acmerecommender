package com.acmerecommender;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import org.apache.mahout.cf.taste.impl.recommender.GenericRecommendedItem;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.javalite.activejdbc.Base;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * A class for handling database jobs
 */
public class Database {

    /**
     * Inserts or updates the taste_preferences table, and returns the preference or -1 if nothing happened
     * @param api_key
     * @param userID
     * @param itemID
     * @param action
     * @return
     */
    public float Store(String api_key, long userID, long itemID, String action) {
        baseOpen();
        UserAccount userAccount = getUserAccount( api_key);

        ActionScore actionScore = userAccount.get(ActionScore.class, "action=?", action).get(0);

        int user_account_id = userAccount.getInteger("id");
        float preference = actionScore.getFloat("value");
        String policy = actionScore.getString("policy");

        List<TastePreference> tastePreferences = TastePreference.where("user_id=? AND item_id=? AND " +
                                                "user_account_id=?", userID, itemID, user_account_id);

        if(!tastePreferences.isEmpty()){
            if( policy.equals("updatePref")) {
                tastePreferences.get(0).setFloat("preference",preference).save();
                return preference;
            }
            else if (policy.equals("greatest") && preference > tastePreferences.get(0).getFloat("preference")) {
                tastePreferences.get(0).setFloat("preference",preference).save();
                return preference;
            }
        }
        else{
            create(userID, itemID, preference, user_account_id);
            return preference;
        }
        baseClose();
        return -1;
    }

    /**
     * Inserts preference into taste_preferences table
     * @param userID
     * @param itemID
     * @param preference
     * @param user_account_id
     */
    private void create( long userID, long itemID, float preference, int user_account_id) {
        TastePreference tastePreference = new TastePreference();
        tastePreference.set("user_id",userID).set("item_id", itemID).set("preference",preference).
                        set("user_account_id",user_account_id).saveIt();
    }

    /**
     * Returns the UserAccount object for api_key
     * @param api_key
     * @return
     */
    private UserAccount getUserAccount(String api_key){
        return UserAccount.findFirst("api_key=?", api_key);
    }

    /**
     * Returns the userAccountID for given api_key
     * @param api_key
     * @return
     */
    public Integer getUserAccountID(String api_key) {
        baseOpen();
        int userAccountID = getUserAccount(api_key).getInteger("id");
        baseClose();
        return userAccountID;
    }

    /**
     * Returns a HashMap with the settings for given user_account_id
     * @param user_account_id
     * @return
     */
    public ArrayList<HashMap> getRecommender(int user_account_id) {
        baseOpen();
        List<Setting> settingList = Setting.where("user_account_id = ?", user_account_id);
        ArrayList<HashMap> mapList = new ArrayList<HashMap>();
        Set<String> columnNames = Setting.attributeNames();
        for (Setting setting : settingList) {
            HashMap row = new HashMap(columnNames.size());
            for (String column : columnNames) {
                row.put(column, setting.get(column));
            }
            mapList.add(row);
        }
        baseClose();
        return mapList;
    }

    /**
     * Returns an ArrayList with all userAccountIDs from table user account
     * @return
     */
    public ArrayList<Integer> userAccountIDs() {
        baseOpen();
        ArrayList<Integer> list = new ArrayList<>();
        List<UserAccount> userAccounts = UserAccount.findBySQL("SELECT id FROM `user_accounts` where exists(select" +
                            " null from taste_preferences where taste_preferences.user_account_id = user_accounts.id)");
        for (int i = 0; i < userAccounts.size(); i++) {
            list.add(userAccounts.get(i).getInteger("id"));
        }
        baseClose();
        return list;
    }

    /**
     * Returns Properties object containing the database settings
     * @return
     */
    public Properties readPropertyFile() {
        String fileName = "/database.properties";
        //Database db = new Database();
        InputStream in = this.getClass().getResourceAsStream(fileName);
        Properties props = new Properties();
        try {
            if (in != null) {
                props.load(in);
            } else {
                FileInputStream fin = new FileInputStream(fileName);
                props.load(fin);
                fin.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return props;
    }

    /**
     * Opens a database connection
     */
    private void baseOpen() {
        MysqlDataSource dataSource = new MysqlDataSource();
        Properties prop = readPropertyFile();
        dataSource.setUrl(prop.get("development.url").toString());
        dataSource.setUser(prop.get("development.username").toString());
        dataSource.setPassword(prop.get("development.password").toString());
        if(!Base.hasConnection()){
            Base.open(dataSource);
        }
    }

    /**
     * Closes a database connection
     */
    private void baseClose(){
        Base.close();
    }

    /**
     * Returns recommendations for user_account_id from top_preferences table
     * @param user_account_id
     * @param howMany
     * @return
     */
    public List<RecommendedItem> topItems( int user_account_id, int howMany){
        baseOpen();
        List<TopPreference> topPreferences = TopPreference
                .where("user_account_id=?", user_account_id)
                .limit(howMany)
                .orderBy("preference DESC");
        List<RecommendedItem> rec = new ArrayList<>();
        for (TopPreference preference : topPreferences) {
            rec.add(new GenericRecommendedItem(preference.getLong("item_id"),
                    preference.getFloat("preference")));
        }
        return rec;
    }
}
