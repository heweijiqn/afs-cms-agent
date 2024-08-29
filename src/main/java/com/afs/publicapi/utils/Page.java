package com.afs.publicapi.utils;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Page<T> {
    public int index = 0;
    public int size = 0;
    public int maxIndex = 0;
    public int totalSize = 0;
    public List<T> list = new ArrayList<>();
}
