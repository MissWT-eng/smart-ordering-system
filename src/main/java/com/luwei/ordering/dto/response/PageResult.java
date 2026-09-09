package com.luwei.ordering.dto.response;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    /**
     *要返回的总记录数
     */
    private Long total;

    /**
     *当前页码
     */
    private Integer pageNum;

    /**
     *每页条数
     */
    private Integer pageSize;

    /**
     *本页数据
     */
    private List<T> list;

    /**
     * 总页数
     */
    private Integer totalPages;

}
