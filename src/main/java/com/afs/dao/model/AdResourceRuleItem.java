package com.afs.dao.model;

import lombok.Data;

import java.util.List;

@Data
public class AdResourceRuleItem {
    public Integer dayStart;
    public Integer dayEnd;
    public List<AdResourceRuleItemDetail> detailList;

    @Data
    public static class AdResourceRuleItemDetail {
        public String action;
        public String condition;
        public String budgetAction;
        public Integer budgetPercent;
    }
}
