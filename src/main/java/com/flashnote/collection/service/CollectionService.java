package com.flashnote.collection.service;

import com.flashnote.collection.dto.CollectionCreateRequest;
import com.flashnote.collection.dto.CollectionUpdateRequest;
import com.flashnote.collection.entity.Collection;

import java.util.List;

public interface CollectionService {
    List<Collection> listCollections(String username);

    Collection createCollection(String username, CollectionCreateRequest request);

    Collection updateCollection(String username, Long id, CollectionUpdateRequest request);

    void deleteCollection(String username, Long id);
}
