package me.zhengziheng.agent.common;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分页结果包络：与 api-contract.md 的分页格式一致。
 * 前端据此渲染列表 + 分页器（total 决定总页数）。
 */
@Data
public class PageResult<T> implements Serializable {

    /** 当前页数据列表 */
    private List<T> list;
    /** 当前页码，从 1 开始 */
    private int page;
    /** 每页条数 */
    private int size;
    /** 总记录数，用于计算总页数 */
    private long total;

    public static <T> PageResult<T> of(List<T> list, int page, int size, long total) {
        PageResult<T> p = new PageResult<>();
        p.setList(list);
        p.setPage(page);
        p.setSize(size);
        p.setTotal(total);
        return p;
    }
}
