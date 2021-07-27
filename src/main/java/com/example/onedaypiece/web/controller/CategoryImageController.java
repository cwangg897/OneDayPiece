package com.example.onedaypiece.web.controller;

import com.example.onedaypiece.service.CategoryImageService;
import com.example.onedaypiece.web.domain.challenge.CategoryName;
import com.example.onedaypiece.web.dto.request.categoryImage.CategoryImageRequestDto;
import com.example.onedaypiece.web.dto.response.category.CategoryImageResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class CategoryImageController {

    private final CategoryImageService categoryImageService;

    @PostMapping("/api/category-image")
    public void postCategoryImage(@RequestBody CategoryImageRequestDto requestDto) {
        categoryImageService.postCategoryImage(requestDto);
    }

    @GetMapping("/api/category-image/{categoryName}")
    public CategoryImageResponseDto getCategoryImage(@PathVariable CategoryName categoryName) {
        return categoryImageService.getCategoryImage(categoryName);
    }

    @DeleteMapping("/api/category-image/{imgUrl}")
    public void deleteCategoryImage(@PathVariable String imgUrl) {
        categoryImageService.deleteCategoryImage(imgUrl);
    }
}