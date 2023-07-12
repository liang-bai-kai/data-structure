package com.hjysite.tree.btree.selfimpl;

/**
 * key value
 */
public record KeyVal<K extends Comparable<K>, V>(K key, V val) {

}
