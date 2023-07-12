package com.hjysite.tree.btree.selfimpl;

import com.hjysite.tree.btree.example.BPlusTree;

import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * B树
 * 1.每个节点最多有m个子节点
 * 2.除根节点和叶子节点外，其他每个节点至少有ceil(m/2)个子节点
 * 3.若根节点不是叶子节点，则至少有两个子节点
 * 4.所有叶子节点都在同一层
 * 5.每个节点中的key都按照从小到大的顺序排列，每个key的左子树中的所有key都小于它，而右子树中的所有key都大于它
 * 6.非叶子节点的key的个数=指向子节点的指针的个数-1
 * 7.非叶子节点的key：ceil(m/2)-1 <= n <= m-1
 * 8.非叶子节点的指针：ceil(m/2) <= n <= m
 * 9.非叶子节点的key和指针的个数：ceil(m/2) <= n <= m-1
 * 10.叶子节点的key：ceil(m/2)-1 <= n <= m-1
 */
public class BTree<K extends Comparable<K>, V> {

    /**
     * 最小度数
     */
    private int d;

    private BTreeNode<K, V> root;


    public BTree(int d) {
        this.d = d;
    }

    /**
     * 查找
     */
    public KeyVal<K, V> search(K key) {
        if (root == null) {
            return null;
        }
        return root.search(key);
    }

    public void put(K key, V val) {
        put(new KeyVal<>(key, val));
    }

    public void put(KeyVal<K, V> keyVal) {
        initRoot();
        BTreeNode<K, V> node = root.searchNode(keyVal.key());
        if (node != null) {
            node.update(keyVal);
            return;
        }
        insert(keyVal);
    }

    /**
     * 插入
     * 注意此时树当中一定不能存在该key
     * @param keyVal
     */
    private void insert(KeyVal<K, V> keyVal) {
        initRoot();
        BTreeNode<K, V> r = root;
        if (root.isFull()) {
            // 根节点已满，需要分裂
            BTreeNode<K, V> newRoot = new BTreeNode<>(d, false);
            newRoot.children[0] = r;
            root = newRoot;
            splitFullNode(newRoot, 0, r);
            insertNonFull(newRoot, keyVal);
        } else {
            insertNonFull(root, keyVal);
        }
    }

    /**
     * 分裂已满节点
     * @param parent 分裂节点的父节点
     * @param index 分裂节点在父节点的子节点数组（children[]）的索引
     * @param node 分裂节点
     */
    private void splitFullNode(BTreeNode<K, V> parent, int index, BTreeNode<K, V> node) {
        BTreeNode<K, V> newNode = new BTreeNode<>(d, node.isLeaf);
        // 将分裂节点的后半部分数据复制到新节点，这里分裂的键值对索引范围是[d, 2d-1)
        for (int i = 0; i < d - 1; i++) {
            newNode.dictionaries[i] = node.dictionaries[i + d];
            node.dictionaries[i + d] = null;
        }

        if (!node.isLeaf) {
            // 将分裂节点的后半部分子节点复制到新节点，注意子节点的最大数量比键值对最大数量多1，所以这里分裂的子节点索引范围是[d, 2d)
            for (int i = 0; i < d; i++) {
                newNode.children[i] = node.children[i + d];
                node.children[i + d] = null;
            }
        }
        newNode.keyNum = d - 1;

        // 将分裂节点的中间键值对提升到父节点
        for (int i = parent.keyNum - 1; i >= index; i--) {
            // 将index（包含）及其后面的键值对向后移动一位，为提升到父节点的键值对和子树腾出位置
            // 因为node是在index位置的子节点，所以node里面所有值都小于index位置的键值对
            parent.dictionaries[i + 1] = parent.dictionaries[i];
        }
        for (int i = parent.keyNum; i > index; i--) {
            // 将index（不包含）及其后面的子节点向后移动一位，为提升到父节点的子树腾出位置
            parent.children[i + 1] = parent.children[i];
        }
        parent.dictionaries[index] = node.dictionaries[d - 1];
        node.dictionaries[d - 1] = null;
        parent.children[index + 1] = newNode;
        node.keyNum = d - 1;
        parent.keyNum++;
    }


    private void insertNonFull(BTreeNode<K, V> node, KeyVal<K, V> keyVal) {
        if (node.isLeaf) {
            // 叶子节点直接插入
            node.insertInside(keyVal);
        } else {
            int index = node.findCanInsertChildrenIndex(keyVal.key());
            if (node.children[index].isFull()) {
                splitFullNode(node, index, node.children[index]);
                index = node.findCanInsertChildrenIndex(keyVal.key());
            }
            insertNonFull(node.children[index], keyVal);
        }
    }

    /**
     * 初始化根节点
     */
    public void initRoot() {
        if (root == null) {
            root = new BTreeNode<>(d, true);
        }
    }

    /**
     * B树节点
     */
    public static class BTreeNode<K extends Comparable<K>, V> {
        // key数量
        private int keyNum;
        // 是否是叶子节点
        private boolean isLeaf;
        // 字典对
        private KeyVal<K, V>[] dictionaries;
        // 子节点
        private BTreeNode<K, V>[] children;

        @SuppressWarnings("unchecked")
        protected BTreeNode(int d, boolean isLeaf) {
            this.keyNum = 0;
            this.isLeaf = isLeaf;
            // 节点的最小深度与每个节点值的最大数量之间的关系是 t = 2 * d - 1，其中 t 是每个节点的最大数量，d 是最小深度。也可以理解为树的最小层数
            // 根节点到叶节点的路径上的节点数量为 d-1（因为最后一层是叶节点，没有子节点）。由于每个节点最多容纳 t-1 个键值对，我们可以得到以下关系：
            // (d-1) * (t-1) ≤ 节点的键值对数量
            // d * t - d - t + 1 ≤ 节点的键值对数量
            //   d * t - d - t + 1
            // = d * t - (d + t) + 1
            // = d * t - (d + t - 1)
            // = d * t - (2 * d - 1)
            // = 1
            this.dictionaries = new KeyVal[2 * d - 1];
            this.children = new BTreeNode[2 * d];
        }

        public boolean isFull() {
            return keyNum == dictionaries.length;
        }

        public int findCanInsertChildrenIndex(K key) {
            for (int index = keyNum - 1; index >= 0; index--) {
                // 从后往前遍历，只到找到第一个比key小的键值对索引，该键值对的右子节点（index + 1）就是要插入的位置
                if (key.compareTo(dictionaries[index].key()) < 0) {
                    return index + 1;
                }
            }
        }

        public int insertInside(KeyVal<K, V> keyVal) {
            // 从后往前遍历，找到插入位置，此时节点没满，最后一个元素一定是null
            for (int i = keyNum - 1; i >= 0; i--) {
                if (keyVal.key().compareTo(dictionaries[i].key()) < 0) {
                    // 如果插入的key比当前key小，则当前key往后移动一位，空出当前位置，并向前继续比较
                    dictionaries[i + 1] = dictionaries[i];
                } else {
                    // 此时插入key大于等于当前key，插入当前位置后一位
                    dictionaries[i + 1] = keyVal;
                    keyNum++;
                    return i + 1;
                }
            }
        }

        /**
         * 内部二分查找
         */
        protected KeyVal<K, V> insideBinarySearch(K key) {
            int index = insideBinarySearchIndex(key);
            return index == -1 ? null : dictionaries[index];
        }

        protected int insideBinarySearchIndex(K key) {
            int low = 0;
            int high = keyNum - 1;
            while (low <= high) {
                int middle = (low + high) / 2;
                if (key.compareTo(dictionaries[middle].key()) < 0) {
                    high = middle - 1;
                } else if (key.compareTo(dictionaries[middle].key()) > 0) {
                    low = middle + 1;
                } else {
                    return middle;
                }
            }
            return -1;
        }

        /**
         * 查找
         */
        protected KeyVal<K, V> search(K key) {
            return Optional.ofNullable(searchNode(key)).map(n -> n.insideBinarySearch(key)).orElse(null);
        }

        protected BTreeNode<K, V> searchNode(K key) {
            for (int i = 0; i < keyNum; i++) {
                if (key.compareTo(dictionaries[i].key()) == 0) {
                    // 存在于当前节点内部中，直接返回
                    return this;
                } else if (key.compareTo(dictionaries[i].key()) < 0) {
                    // 如果小于当前key
                    if (!isLeaf && children[i] != null) {
                        // 如果不是叶子节点，且子节点不为空，则递归查找
                        return children[i].searchNode(key);
                    }
                }
            }
            return null;
        }

        protected KeyVal<K, V> getKeyVal(int index) {
            if (index < 0 || index >= keyNum) {
                return null;
            }
            return dictionaries[index];
        }

        protected void update(KeyVal<K, V> keyVal) {
            int index = insideBinarySearchIndex(keyVal.key());
            if (index == -1) {
                throw new NoSuchElementException("key not found in this btree node");
            }
            dictionaries[index] = keyVal;
        }

    }

}
