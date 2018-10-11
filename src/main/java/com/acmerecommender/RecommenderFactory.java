package com.acmerecommender;

import org.apache.mahout.cf.taste.common.NoSuchUserException;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.common.Weighting;
import org.apache.mahout.cf.taste.impl.neighborhood.CachingUserNeighborhood;
import org.apache.mahout.cf.taste.impl.neighborhood.NearestNUserNeighborhood;
import org.apache.mahout.cf.taste.impl.neighborhood.ThresholdUserNeighborhood;
import org.apache.mahout.cf.taste.impl.recommender.*;
import org.apache.mahout.cf.taste.impl.recommender.svd.ALSWRFactorizer;
import org.apache.mahout.cf.taste.impl.recommender.svd.SVDRecommender;
import org.apache.mahout.cf.taste.impl.similarity.*;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.neighborhood.UserNeighborhood;
import org.apache.mahout.cf.taste.recommender.RecommendedItem;
import org.apache.mahout.cf.taste.recommender.Recommender;
import org.apache.mahout.cf.taste.similarity.ItemSimilarity;
import org.apache.mahout.cf.taste.similarity.UserSimilarity;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A class that produces the recommendations
 */

public class RecommenderFactory {
    private Recommender recommender;
    private DataModel model;
    private HashMap<String,HashMap> RecommenderOptions;
    private int user_account_id;

    RecommenderFactory( int user_account_id) {
        RecommenderOptions = new HashMap<String,HashMap>();
        this.user_account_id = user_account_id;
        try {
            model = RecommenderDatamodel.thesisMySQLmodel( user_account_id);
        } catch (TasteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns an SVDRecommender
     * @return
     * @throws TasteException
     */
    public Recommender svdRec() throws TasteException {
        return new SVDRecommender(model, new ALSWRFactorizer
                (model, 10, 0.05, 10));
    }

    /**
     * Returns a Caching Recommender wrapped a User Based Recommender with LogLikelihoodSimilarity
     * @return
     * @throws TasteException
     */
    public Recommender userLogLikeRec() throws TasteException {
        UserSimilarity similarity = new CachingUserSimilarity(
                new LogLikelihoodSimilarity(model), model);

        return new CachingRecommender(new GenericUserBasedRecommender(
                model, getNeighborhood( "userLogLikeRec", similarity, model), similarity));
    }

    public Recommender userSpearRec() throws TasteException {
        UserSimilarity similarity = new CachingUserSimilarity(
                new SpearmanCorrelationSimilarity(model), model);

        return new CachingRecommender( new GenericUserBasedRecommender(
                model, getNeighborhood( "userSpearRec", similarity, model), similarity));
    }

    public Recommender userEuclRec() throws TasteException {
        UserSimilarity similarity = new CachingUserSimilarity(
                new EuclideanDistanceSimilarity(model), model);

        return new CachingRecommender( new GenericUserBasedRecommender(
                model, getNeighborhood( "userEuclRec", similarity, model), similarity));
    }

    public Recommender userPearRec() throws TasteException {
        UserSimilarity similarity = new CachingUserSimilarity(
                new PearsonCorrelationSimilarity(model, Weighting.WEIGHTED), model);

        return new CachingRecommender( new GenericUserBasedRecommender(
                model, getNeighborhood( "userPearRec", similarity, model), similarity));
    }

    public Recommender userTaniRec() throws TasteException {
        UserSimilarity similarity = new CachingUserSimilarity(
                new TanimotoCoefficientSimilarity( model), model);

        return new CachingRecommender( new GenericUserBasedRecommender(
                model, getNeighborhood( "userTaniRec", similarity, model), similarity));
    }

    /**
     * Returns a Caching Recommender wrapped an Item Based Recommender with LogLikelihoodSimilarity
     * @return
     * @throws TasteException
     */
    public Recommender itemLogLikeRec() throws TasteException {
        ItemSimilarity similarity = new CachingItemSimilarity( new LogLikelihoodSimilarity(model), model);
        return new CachingRecommender( new GenericItemBasedRecommender( model, similarity));
    }

    public Recommender itemEuclRec() throws TasteException {
        ItemSimilarity similarity = new CachingItemSimilarity( new EuclideanDistanceSimilarity(model), model);
        return new CachingRecommender( new GenericItemBasedRecommender( model, similarity));
    }

    public Recommender itemPearRec() throws TasteException {
        ItemSimilarity similarity = new CachingItemSimilarity( new PearsonCorrelationSimilarity(model, Weighting.WEIGHTED), model);
        return new CachingRecommender( new GenericItemBasedRecommender( model, similarity));
    }

    public Recommender itemTaniRec() throws TasteException {
        ItemSimilarity similarity = new CachingItemSimilarity( new TanimotoCoefficientSimilarity(model), model);
        return new CachingRecommender( new GenericItemBasedRecommender( model, similarity));
    }

    private Recommender itemAvRec() throws TasteException {
        return new ItemAverageRecommender( model);
    }

    private Recommender itemUserAvRec() throws TasteException {
        return new CachingRecommender( new ItemUserAverageRecommender( model));
    }


    /**
     * Gets reccommenderList from database, itterates the list, gets recommender name and invokes it to recommender,
     * Returns recommendations
     * @param userID
     * @param howMany
     * @return
     * @throws TasteException
     * @throws IllegalAccessException
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     */
    public List<RecommendedItem> recommend(long userID, int howMany) throws TasteException,
            IllegalAccessException, NoSuchMethodException,
            InvocationTargetException {
        List<RecommendedItem> recommendations = new ArrayList<>();
        Database db = new Database();

        ArrayList<HashMap> reccommenderList = db.getRecommender( user_account_id);
        if( reccommenderList.size() > 1) {
            howMany = howMany / reccommenderList.size() ;
        }

        try {
            if( model.getNumItems() > 0) {
                for (HashMap setting : reccommenderList) {
                    String key = (String) setting.get("recommender");
                    RecommenderOptions.put(key, setting);
                    Method method = this.getClass().getMethod((String) setting.get("recommender"));
                    this.recommender = (Recommender) method.invoke(this);
                    List<RecommendedItem> testrecommendations = recommender.recommend(userID, howMany);
                    if (!recommendations.isEmpty()) {
                        ArrayList<Long> recItemIDs = new ArrayList<>();
                        for (RecommendedItem recommendedItem : recommendations) {
                            recItemIDs.add(recommendedItem.getItemID());
                        }
                        for (RecommendedItem item : testrecommendations) {
                            if (!recItemIDs.contains(item.getItemID())) {
                                recommendations.add(item);
                            }
                        }
                    } else {
                        recommendations.addAll(testrecommendations);
                        if (reccommenderList.size() > 1) {
                            howMany += howMany;
                        }
                    }
                }

                // Fallback Recommender
                if( recommendations.isEmpty()){
                    recommendations.addAll( itemAvRec().recommend( userID, howMany));
                }
            }
        }catch (NoSuchUserException ignored) {
            howMany = howMany * reccommenderList.size();
        }

        if( recommendations.isEmpty()){
            recommendations.addAll( db.topItems(user_account_id, howMany));
        }

        return recommendations.stream().limit(howMany).collect(Collectors.toList());
    }

    public DataModel getDataModel() {
        return model;
    }


    /**
     * Returns UserSimilarity
     * @param rec
     * @param similarity
     * @param model
     * @return
     * @throws TasteException
     */
    private UserNeighborhood getNeighborhood(String rec, UserSimilarity similarity, DataModel model)
            throws TasteException {
        HashMap settings = RecommenderOptions.get(rec);
        String neighborhood = (String) settings.get("neighborhood");
        if( neighborhood.equals("ThresholdUserNeighborhood")){
            double neighborhood_value = ((Number)settings.get("neighborhood_value")).doubleValue();
            return new CachingUserNeighborhood(
                    new ThresholdUserNeighborhood(neighborhood_value, similarity, model), model);
        }
        else{
            int neighborhood_value = ((Number)settings.get("neighborhood_value")).intValue();
            return new CachingUserNeighborhood(
                    new NearestNUserNeighborhood(neighborhood_value, similarity, model), model);
        }
    }
}
