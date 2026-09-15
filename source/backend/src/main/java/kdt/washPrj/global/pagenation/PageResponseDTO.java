package kdt.washPrj.global.pagenation;

import lombok.Getter;

import java.util.List;

@Getter
public class PageResponseDTO<T> {
    private List<T> content;
    private int page;
    private int size;
    private int totalElements;
    private int totalPages;
    private int startPage;
    private int endPage;
    private boolean hasPrevious;
    private boolean hasNext;

    public PageResponseDTO(List<T> content, int page, int size, int totalElements) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = (int) Math.ceil((double) totalElements / size);

        int pageLimit = 5;
        this.startPage = ((page - 1) / pageLimit) * pageLimit + 1;
        this.endPage = Math.min(startPage + pageLimit - 1, totalPages);

        this.hasPrevious = page > 1;
        this.hasNext = page < totalPages;
    }
}
