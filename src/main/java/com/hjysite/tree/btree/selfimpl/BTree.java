package com.hjysite.tree.btree.selfimpl;

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
        this.root = new BTreeNode<>(d, true);
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
     *
     * @param keyVal
     */
    private void insert(KeyVal<K, V> keyVal) {
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
     *
     * @param parent 分裂节点的父节点
     * @param index  分裂节点在父节点的子节点数组（children[]）的索引
     * @param node   分裂节点
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

    public void delete(K key) {
        if (search(key) == null) {
            return;
        }
        delete(root, key);
    }

    private void delete(BTreeNode<K, V> node, K key) {
        int i = node.insideBinarySearchIndex(key);
        if (i != -1) {
            // 存在于节点内部中
            if (node.isLeaf) {
                // 1. 如果key在叶节点x中，则直接从node中删除key，
                // 因为删除搜索是自跟向下，情况2和3会保证当再叶子节点找到target时，肯定能借节点或合并成功而不会引起父节点的关键字个数少于t-1。
                node.deleteInside(i, BTreeNode.LEFT_CHILD_NODE);
            } else {
                // 2. 如果key在内部节点中
                BTreeNode<K, V> leftChildNode = node.children[i + BTreeNode.LEFT_CHILD_NODE];
                BTreeNode<K, V> rightChildNode = node.children[i + BTreeNode.RIGHT_CHILD_NODE];
                if (leftChildNode.leanable()) {
                    // 2a. 如果左子节点至少含有 d 个 key，向下查找最大的key，即最左边的key（一定小于被删除的key）
                    BTreeNode<K, V> predecessor = leftChildNode;
                    BTreeNode<K, V> erasureNode = leftChildNode;
                    while (!predecessor.isLeaf) {
                        erasureNode = predecessor;
                        predecessor = predecessor.children[predecessor.keyNum];
                    }
                    node.dictionaries[i] = predecessor.dictionaries[predecessor.keyNum - 1];
                    delete(erasureNode, node.dictionaries[i].key());
                } else if (rightChildNode.leanable()) {
                    // 2b. 如果左子节点少于 d 个 key，又子节点至少含有 d 个 key，向下查找最小的key，即最右边的key（一定大于被删除的key）
                    BTreeNode<K, V> successor = rightChildNode;
                    BTreeNode<K, V> erasureNode = rightChildNode;
                    while (!successor.isLeaf) {
                        erasureNode = successor;
                        successor = successor.children[0];
                    }
                    node.dictionaries[i] = successor.dictionaries[0];
                    delete(erasureNode, node.dictionaries[i].key());
                } else {
                    // 2c. 如果左子节点少于 d 个 key, 右子节点少于 d 个 key，合并左右子节点，并将合并后最中间的key提升到父节点
                    int middleIndex = mergeNode(rightChildNode, leftChildNode);
                    // 将节点中需要删除的key下沉到合并后的左子节点中的空位，并且删除需要删除的key的右子节点（右子节点已经被合并到左子节点）
                    moveKey(node, i, BTreeNode.RIGHT_CHILD_NODE, leftChildNode, middleIndex);
                    delete(leftChildNode, key);
                }
            }
        } else {
            // 3. 不在当前节点内部，向下查找子树，在向下查找的过程中，如果发现某个节点的关键字个数不大于d，说明删除后可能需要重新平衡树
            // 请注意，此条件需要比通常 B 树条件所需的最小值多一个键。这种强化的条件允许我们在一次向下传递中从树中删除一个键，而无需“备份”。
            i = node.subtreeRootNodeIndex(key);
            BTreeNode<K, V> child = node.children[i]; // childNode is i-th child of node.
            if (child.keyNum <= d - 1) {
                BTreeNode<K, V> leftChildSibling = (i - 1 >= 0) ? node.children[i - 1] : null;
                BTreeNode<K, V> rightChildSibling = (i + 1 <= node.keyNum) ? node.children[i + 1] : null;

                if (leftChildSibling != null && leftChildSibling.keyNum >= d) {
                    // 3a. 如果子节点的左兄弟至少含有 d 个 key，左边的key下城，子节点左兄弟的最后一个key上升到父节点
                    child.shiftRightByOne();
                    // i -1 位置的key一定比child的所有值都小
                    child.dictionaries[0] = node.dictionaries[i - 1];
                    if (!child.isLeaf) {
                        child.children[0] = leftChildSibling.children[leftChildSibling.keyNum];
                    }
                    child.keyNum++;

                    node.dictionaries[i - 1] = leftChildSibling.dictionaries[leftChildSibling.keyNum - 1];
                    leftChildSibling.deleteInside(leftChildSibling.keyNum - 1, BTreeNode.RIGHT_CHILD_NODE);
                } else if (rightChildSibling != null && rightChildSibling.keyNum >= d) {
                    // 3b. 如果子节点的右兄弟至少含有 d 个 key，右边的key下城，子节点右兄弟的第一个key上升到父节点
                    child.dictionaries[child.keyNum] = node.dictionaries[i];
                    if (!child.isLeaf) {
                        child.children[child.keyNum + 1] = rightChildSibling.children[0];
                    }
                    child.keyNum++;

                    node.dictionaries[i] = rightChildSibling.dictionaries[0];
                    rightChildSibling.deleteInside(0, BTreeNode.LEFT_CHILD_NODE);
                } else {
                    if (leftChildSibling != null) {
                        int emptyKeyIndex = mergeNode(leftChildSibling, child);
                        moveKey(node, i - 1, BTreeNode.LEFT_CHILD_NODE, child, emptyKeyIndex); // i - 1 is the median key index in node when merging with the left sibling.
                    } else if (rightChildSibling != null) {
                        int emptyKeyIndex = mergeNode(rightChildSibling, child);
                        moveKey(node, i, BTreeNode.RIGHT_CHILD_NODE, child, emptyKeyIndex); // i is the median key index in node when merging with the right sibling.
                    }
                }
            }
            delete(child, key);
        }

    }

    /**
     * 将src节点合并到dest节点
     * 注意：两个节点的keyNum都不大于d - 1，否则合并之后的节点大小可能大于2d - 1
     * 合并之后原本src的键值对 和 原本dest的键值对 之间会有一个空位，需要空出一位不然子节点会冲突
     *
     * @param src  源节点
     * @param dest 目标节点
     * @return 合并后，空出一位键值对位置的索引
     */
    private int mergeNode(BTreeNode<K, V> src, BTreeNode<K, V> dest) {
        int middleIndex;
        if (src.dictionaries[src.keyNum - 1].key().compareTo(dest.dictionaries[0].key()) < 0) {
            middleIndex = src.keyNum;
            // src 的所有值都小于 dest 的所有值, 将所有dest的键值对向右移动src.keyNum + 1（多空出一位），为src的键值对腾出空间,
            for (int j = dest.keyNum - 1; j >= 0; j--) {
                dest.dictionaries[j + src.keyNum + 1] = dest.dictionaries[j];
                if (!dest.isLeaf) {
                    dest.children[j + src.keyNum + 2] = dest.children[j + 1];
                }
            }
            // 将src的键值对复制到dest中
            for (int j = 0; j < src.keyNum; j++) {
                dest.dictionaries[j] = src.dictionaries[j];
                if (!src.isLeaf) {
                    dest.children[j] = src.children[j];
                }
            }
            if (!src.isLeaf) {
                dest.children[src.keyNum] = src.children[src.keyNum];
            }

        } else {
            // src 的所有值都大于 dest 的所有值, 直接空出一位再将src追加到dest后面
            middleIndex = dest.keyNum;
            int offset = dest.keyNum + 1;
            for (int j = 0; j < src.keyNum; j++) {
                dest.dictionaries[j + offset] = src.dictionaries[j];
                if (!src.isLeaf) {
                    dest.children[j + offset] = src.children[j + 1];
                }
            }
            if (!src.isLeaf) {
                dest.children[offset + src.keyNum] = src.children[src.keyNum];
            }

        }
        dest.keyNum += src.keyNum;
        return middleIndex;
    }

    /**
     * 将src节点中的键值对下沉到dest节点中(dest节点已经空出来一个位置用于下沉)
     *
     * @param src           源节点
     * @param srcKeyIndex   源节点中需要下沉的键值对的索引
     * @param childIndex    源节点中需要下沉的键值对的左/右子节点，会被删除
     * @param dest          目标节点
     * @param emptyKeyIndex 目标节点中空出来的位置的索引
     */
    private void moveKey(BTreeNode<K, V> src, int srcKeyIndex, int childIndex, BTreeNode<K, V> dest, int emptyKeyIndex) {
        dest.dictionaries[emptyKeyIndex] = src.dictionaries[srcKeyIndex];
        dest.keyNum++;
        src.deleteInside(srcKeyIndex, childIndex);
        if (src == root && src.keyNum == 0) {
            root = dest;
        }
    }

    /**
     * B树节点
     */
    public static class BTreeNode<K extends Comparable<K>, V> {
        private int d;
        // key数量
        private int keyNum;
        // 是否是叶子节点
        private boolean isLeaf;
        // 字典对
        private KeyVal<K, V>[] dictionaries;
        // 子节点
        private BTreeNode<K, V>[] children;

        public static final int LEFT_CHILD_NODE = 0;
        public static final int RIGHT_CHILD_NODE = 1;

        private int subtreeRootNodeIndex(K key) {
            for (int i = 0; i < keyNum; i++) {
                if (key.compareTo(dictionaries[i].key()) < 0) {
                    return i;
                }
            }
            return keyNum;
        }

        private void shiftRightByOne() {
            for (int i = keyNum; i > 0; i--) {
                dictionaries[i] = dictionaries[i - 1];
                if (!isLeaf) {
                    children[i + 1] = children[i];
                }
            }
            if (!isLeaf) {
                children[1] = children[0];
            }
        }

        @SuppressWarnings("unchecked")
        protected BTreeNode(int d, boolean isLeaf) {
            this.d = d;
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

        public boolean leanable() {
            return keyNum >= d;
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
            return 0;
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
            return 0;
        }

        /**
         * 删除节点中的键值对
         *
         * @param index                 要删除的键值对索引
         * @param leftOrRightChildIndex 要删除的键值对的左子节点索引或右子节点索引
         * @return
         */
        private BTreeNode<K, V> deleteInside(int index, int leftOrRightChildIndex) {
            BTreeNode<K, V> leftOrRightChildNode = children[index + leftOrRightChildIndex];
            // 从index开始，后面的元素往前移动一位，自然覆盖了index位置的元素
            for (int i = index; i < keyNum - 1; i++) {
                dictionaries[i] = dictionaries[i + 1];
                children[i + leftOrRightChildIndex] = children[i + leftOrRightChildIndex + 1];
            }
            dictionaries[keyNum - 1] = null;
            keyNum--;
            return leftOrRightChildNode;
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
