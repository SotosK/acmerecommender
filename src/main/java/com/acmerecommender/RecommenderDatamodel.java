package com.acmerecommender;

import com.mysql.jdbc.jdbc2.optional.MysqlDataSource;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.impl.model.jdbc.ConnectionPoolDataSource;
import org.apache.mahout.cf.taste.model.DataModel;

import java.util.Properties;

/**
 * A Class that returns the datamodel
 */

public class RecommenderDatamodel{

    public static DataModel thesisMySQLmodel( int user_account_id) throws TasteException {
        MysqlDataSource dataSource = new MysqlDataSource();

        Database db = new Database();
        Properties prop = db.readPropertyFile();

        dataSource.setUrl(prop.get("development.url").toString());
        dataSource.setUser(prop.get("development.username").toString());
        dataSource.setPassword(prop.get("development.password").toString());
        dataSource.setCachePreparedStatements(true);
        dataSource.setCachePrepStmts(true);
        dataSource.setCacheResultSetMetadata(true);
        dataSource.setAlwaysSendSetIsolation(false);
        dataSource.setElideSetAutoCommits(true);

        return new OverrideReloadFromJDBCDataModel(new OverrideMySQLJBDCDataModel
                ( new ConnectionPoolDataSource(dataSource),
                        "taste_preferences", "user_id",
                        "item_id", "user_account_id",
                        user_account_id, "preference", null));
    }
}
