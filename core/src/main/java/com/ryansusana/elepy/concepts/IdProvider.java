package com.ryansusana.elepy.concepts;

import com.ryansusana.elepy.dao.Crud;

public abstract class IdProvider<T> {
    public abstract String getId(Crud<T> dao);
}
