package com.siqian.qianpicturebackend.api.imageSearch;

import com.siqian.qianpicturebackend.api.imageSearch.model.ImageSearchResult;
import com.siqian.qianpicturebackend.api.imageSearch.sub.GetImageFirstUrlApi;
import com.siqian.qianpicturebackend.api.imageSearch.sub.GetImageListApi;
import com.siqian.qianpicturebackend.api.imageSearch.sub.GetImagePageUrlApi;

import java.util.List;

/**
 * 图片搜索API门面，组合了以图搜图的三个步骤
 */
public class ImageSearchApiFacade {

    /**
     * 搜索图片
     */
    public static List<ImageSearchResult> searchImages(String imageUrl){
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);
        List<ImageSearchResult> imageList = GetImageListApi.getImageList(imageFirstUrl);
        return imageList;
    }

    public static void main(String[] args) {
        List<ImageSearchResult> imageSearchResults = ImageSearchApiFacade.searchImages("https://www.codefather.cn/logo.png");
        System.out.println(imageSearchResults);
    }


}
