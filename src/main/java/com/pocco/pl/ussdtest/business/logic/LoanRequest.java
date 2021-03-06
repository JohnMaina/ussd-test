/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.pocco.pl.ussdtest.business.logic;

import com.pocco.pl.ussdtest.model.LoanRequestModel;
import com.pocco.pl.ussdtest.model.USSDRequestModel;
import com.pocco.pl.ussdtest.params.LoanParams;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

/**
 *
 * @author sure
 */
public class LoanRequest {

    private final LoanParams loanParams = new LoanParams();

    public Map init(Map<String, String> sessionMap, USSDRequestModel mnoSession) {

        String statusLevel = sessionMap.get("statusLevel");
        String ussdType = "continue";
        String responseString = "";

        switch (statusLevel) {
            case "1":
                responseString = "Please select the Loan Type\n";
                HashMap<String, JSONObject> loanTypes = LoanParams.getLoanTypes();
                Integer x = 1;
                String loanTypeIds = "";
                String loanOutStandings = "";
                for (String loanId : loanTypes.keySet()) {
                    JSONObject loan = loanTypes.get(loanId);

                    loanTypeIds = loanTypeIds + loan.get("loanTypeId").toString();
                    responseString = responseString + x + ". " + loan.get("loanName");
                    if (x < loanTypes.size()) {
                        responseString = responseString + "\n";
                        loanTypeIds = loanTypeIds + "|";
                    }
                    x++;

                }
                sessionMap.put("loanTypeIds", loanTypeIds);
                break;
            case "2":
                Integer ussdBody = Integer.parseInt(mnoSession.getUssdBody().replaceAll("[^\\d.]", "")) - 1;
                System.out.println();
                String LoanIds[] = sessionMap.get("loanTypeIds").split("\\|");
                JSONObject loanType = LoanParams.getLoanType(LoanIds[ussdBody]);

                if (loanType == null) {
                    responseString = "Invalid Menu. Please try again";
                    ussdType = "repeat";
                } else {
                    Boolean processLoan = false;
                    LoanRequestModel existingLoan = loanParams.getLoanRequest(sessionMap.get("idNumber"), loanType.get("loanTypeId").toString());
                    if (existingLoan == null) {
//                    System.out.println(existingLoan);
                        processLoan = true;
                    } else {
                        BigDecimal outStandingLoan = new BigDecimal(existingLoan.getLoanOutstanding());
                        if (outStandingLoan.compareTo(new BigDecimal(0)) != 0) {
                            ussdType = "end";
                            responseString = "You have an outstanding loan of UGX " + outStandingLoan;
                        } else {
                            processLoan = true;
                        }

                    }

                    if (processLoan) {
                        responseString = "Enter loan amount to apply. Loan amount must be between " + loanType.get("minimumLoanAmount") + " and " + loanType.get("maximumLoanAmount");
                        sessionMap.put("minimumLoanAmount", loanType.get("minimumLoanAmount").toString());
                        sessionMap.put("maximumLoanAmount", loanType.get("maximumLoanAmount").toString());
                        sessionMap.put("loanTypeId", loanType.get("loanTypeId").toString());
                        sessionMap.put("loanTenure", loanType.get("loanTenure").toString());
                        sessionMap.put("loanInterest", loanType.get("loanInterest").toString());
                        sessionMap.put("loanName", loanType.get("loanName").toString());
                    }
                }

                break;
            case "3":
                String ussdBodyAmount = mnoSession.getUssdBody().replaceAll("[^\\d.]", "");
                BigDecimal loanAmount = new BigDecimal(ussdBodyAmount);
                BigDecimal minimumLoanAmount = new BigDecimal(sessionMap.get("minimumLoanAmount"));
                BigDecimal maximumLoanAmount = new BigDecimal(sessionMap.get("maximumLoanAmount"));
                BigDecimal loanInterest = new BigDecimal(sessionMap.get("loanInterest"));
                BigDecimal loanOutStanding = loanAmount.multiply((loanInterest.add(new BigDecimal(100))).divide(new BigDecimal(100)));

                if ((maximumLoanAmount.compareTo(loanAmount) == 1 || maximumLoanAmount.compareTo(loanAmount) == 0) && loanAmount.compareTo(minimumLoanAmount) == 1) {
                    responseString = "Enter the repayment period in months (Maximum " + sessionMap.get("loanTenure") + ")";
                } else {
                    ussdType = "repeat";
                    responseString = "Loan Amount must be between " + minimumLoanAmount + " and " + maximumLoanAmount;
                }
                sessionMap.put("loanAmount", loanAmount.toString());
                sessionMap.put("loanOutstandingAmount", loanOutStanding.toString());
                break;
            case "4":
                Integer loanTenure = Integer.parseInt(mnoSession.getUssdBody().replaceAll("[^\\d.]", ""));

                if (loanTenure > Integer.parseInt(sessionMap.get("loanTenure")) || loanTenure < 1) {
                    ussdType = "repeat";
                    responseString = "Invalid loan Tenure. Please try again";
                } else {
                    responseString = "Accept terms and conditions\n1. Accept\n2. Decline";
                }
                sessionMap.put("loanTenure", loanTenure + "");

                break;
            case "5":
                Integer tnc = Integer.parseInt(mnoSession.getUssdBody().replaceAll("\\W", ""));
                LocalDate now = LocalDate.now();
                LocalDate duedate = now.plusMonths(Integer.parseInt(sessionMap.get("loanTenure")));
                if (tnc == 1) {

                    loanParams.addLoanRequest(
                            sessionMap.get("idNumber"),
                            sessionMap.get("loanTypeId"),
                            new LoanRequestModel(
                                    sessionMap.get("firstName"),
                                    sessionMap.get("loanTypeId"),
                                    sessionMap.get("loanName"),
                                    sessionMap.get("loanAmount"),
                                    sessionMap.get("loanTenure"),
                                    sessionMap.get("idNumber"),
                                    sessionMap.get("loanInterest"),
                                    sessionMap.get("loanOutstandingAmount"),
                                    now.toString(),
                                    duedate.toString(),
                                    tnc + "")
                    );

                    responseString = "Congratulations " + sessionMap.get("firstName") + ", You have been awarded a loan of UGX" + sessionMap.get("loanAmount") + " at an interest of " + sessionMap.get("loanInterest") + "%. Your outstanding loan is UGX" + sessionMap.get("loanOutstandingAmount") + " your payment will be due on " + duedate + ". Thank you for choosing pocco credits";
                    ussdType = "end";
                } else {
                    if (tnc == 2) {
                        responseString = "To access Pocco credit loans, you must accept terms and conditions. Please dial " + mnoSession.getServiceCode() + " to try again";
                        ussdType = "end";
                    } else {
                        ussdType = "repeat";
                        responseString = "Invalid TnC response\nAccept terms and conditions\n1. Accept\n2. Decline";
                    }
                }

                sessionMap.put("tnc", tnc + "");
                break;

            default:
                responseString = "An error occured please try again later";
                ussdType = "end";
                break;

        }

        sessionMap.put("responseString", responseString);
        sessionMap.put("ussdType", ussdType);

        return sessionMap;
    }
}
