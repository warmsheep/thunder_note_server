package com.flashnote.collection.controller;

import com.flashnote.collection.dto.CollectionCreateRequest;
import com.flashnote.collection.dto.CollectionResponse;
import com.flashnote.collection.dto.CollectionUpdateRequest;
import com.flashnote.collection.entity.Collection;
import com.flashnote.collection.service.CollectionService;
import com.flashnote.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/collections")
public class CollectionController {
    private final CollectionService collectionService;

    public CollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @PostMapping("/list")
    public ApiResponse<List<CollectionResponse>> list(Authentication authentication) {
        return ApiResponse.success(collectionService.listCollections(authentication.getName()).stream()
                .map(this::toResponse)
                .toList());
    }

    @PostMapping
    public ApiResponse<CollectionResponse> create(Authentication authentication,
                                          @Valid @RequestBody CollectionCreateRequest request) {
        return ApiResponse.success(toResponse(collectionService.createCollection(authentication.getName(), request)));
    }

    @PutMapping("/{id}")
    public ApiResponse<CollectionResponse> update(Authentication authentication,
                                           @PathVariable @Positive Long id,
                                           @Valid @RequestBody CollectionUpdateRequest request) {
        return ApiResponse.success(toResponse(collectionService.updateCollection(authentication.getName(), id, request)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(Authentication authentication,
                                    @PathVariable @Positive Long id) {
        collectionService.deleteCollection(authentication.getName(), id);
        return ApiResponse.success("Deleted", null);
    }

    private CollectionResponse toResponse(Collection collection) {
        if (collection == null) {
            return null;
        }
        CollectionResponse response = new CollectionResponse();
        response.setId(collection.getId());
        response.setUserId(collection.getUserId());
        response.setName(collection.getName());
        response.setDescription(collection.getDescription());
        response.setCreatedAt(collection.getCreatedAt());
        response.setUpdatedAt(collection.getUpdatedAt());
        return response;
    }
}
