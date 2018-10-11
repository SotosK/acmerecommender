package com.acmerecommender;

/**
 * A class to validate inputs from the servlet response
 */

public class Validator {
    private String status="success";
    private String message= "";
    private Boolean isValid=true;

    Validator(String host, String userIDString, String api_key, String itemIDString, String action){
        Database db = new Database();
        try{
            int userAccountID = db.getUserAccountID(api_key);
        }catch (NullPointerException e){
            status = "error";
            message = "No user account found for that api key.";
            isValid = false;
        }

        if( userIDString == null || userIDString.isEmpty()){
            status = "error";
            message += "Missing parameter userID. ";
            isValid = false;
        }
        if( (itemIDString.isEmpty() && !action.isEmpty()) || (!itemIDString.isEmpty() && action.isEmpty())){
            status = "error";
            message += "Missing parameter itemID or parameter action.";
            isValid = false;
        }
        if(message.equals("")){
            message = "OK";
        }
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Boolean getValid() {
        return isValid;
    }
}
