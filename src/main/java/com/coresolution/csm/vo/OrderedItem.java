package com.coresolution.csm.vo;

import lombok.Data;

@Data
public class OrderedItem {
    private String column; // DB 컬럼명 'coulmn'에 들어갈 값 (오타 아님!)
    private String comment;
    private Integer turn; // null 가능
    private String viewYn; // 'y' or 'n'

}
