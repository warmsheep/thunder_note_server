package com.flashnote.collection.service;

import com.flashnote.collection.entity.Collection;

import java.util.List;

public interface CollectionService {
    List<Collection> listCollections(String username);

    Collection createCollection(String username, Collection collection);

    Collection updateCollection(String username, Long id, Collection collection);

    void deleteCollection(String username, Long id);
}
