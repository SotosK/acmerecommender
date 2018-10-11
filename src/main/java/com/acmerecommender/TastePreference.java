package com.acmerecommender;

import org.javalite.activejdbc.Model;
import org.javalite.activejdbc.annotations.CompositePK;

@CompositePK({ "user_id", "item_id", "user_account_id" })
public class TastePreference extends Model{
}
