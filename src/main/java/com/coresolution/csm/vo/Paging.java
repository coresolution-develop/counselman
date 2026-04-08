package com.coresolution.csm.vo;

public class Paging {

    private int totalCount; // 전체 데이터 개수
    private int displayPageNum = 20; // 한 번에 보여줄 페이지 개수
    private int startPage; // 화면에서 보이는 첫 번째 페이지 번호
    private int endPage; // 화면에서 보이는 마지막 페이지 번호
    private boolean prev; // 이전 버튼 활성화 여부
    private boolean next; // 다음 버튼 활성화 여부
    private int firstPage = 1; // 첫 페이지 (항상 1)
    private int lastPage; // 마지막 페이지 번호
    private Criteria cri; // 현재 페이지 정보

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
        calculatePaging();
    }

    private void calculatePaging() {
        if (cri == null) {
            return;
        }

        int page = cri.getPage(); // 현재 페이지 번호
        int perPageNum = cri.getPerPageNum(); // 한 페이지당 데이터 개수

        // 🔹 전체 페이지 수 계산 (총 데이터 개수 / 페이지당 개수)
        lastPage = (int) Math.ceil((double) totalCount / perPageNum);

        // 🔹 화면에서 보이는 마지막 페이지 계산
        endPage = (int) (Math.ceil(page / (double) displayPageNum) * displayPageNum);

        // 🔹 화면에서 보이는 첫 페이지 계산
        startPage = endPage - displayPageNum + 1;
        if (startPage < 1)
            startPage = 1;

        // 🔹 실제 존재하는 마지막 페이지보다 크면 조정
        if (endPage > lastPage) {
            endPage = lastPage;
        }

        // 🔹 이전 버튼 활성화 여부
        prev = startPage > 1;

        // 🔹 다음 버튼 활성화 여부
        next = endPage < lastPage;
    }

    public int getFirstPage() {
        return firstPage;
    }

    public int getLastPage() {
        return lastPage;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public int getDisplayPageNum() {
        return displayPageNum;
    }

    public void setDisplayPageNum(int displayPageNum) {
        this.displayPageNum = displayPageNum;
    }

    public int getStartPage() {
        return startPage;
    }

    public int getEndPage() {
        return endPage;
    }

    public boolean isPrev() {
        return prev;
    }

    public boolean isNext() {
        return next;
    }

    public Criteria getCri() {
        return cri;
    }

    public void setCri(Criteria cri) {
        this.cri = cri;
        calculatePaging(); // cri가 설정될 때 다시 페이징 계산
    }

    @Override
    public String toString() {
        return "Paging [totalCount=" + totalCount + ", displayPageNum=" + displayPageNum + ", startPage=" + startPage
                + ", endPage=" + endPage + ", prev=" + prev + ", next=" + next + ", firstPage=" + firstPage
                + ", lastPage=" + lastPage + ", cri=" + cri + "]";
    }
}
