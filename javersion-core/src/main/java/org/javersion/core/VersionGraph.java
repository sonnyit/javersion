/*
 * Copyright 2013 Samppa Saarela
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.javersion.core;

import static com.google.common.collect.Iterables.getFirst;
import static com.google.common.collect.Iterables.transform;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toSet;
import static org.javersion.core.BranchAndRevision.max;
import static org.javersion.core.BranchAndRevision.min;
import static org.javersion.util.MapUtils.mapValueFunction;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.javersion.util.PersistentMap;
import org.javersion.util.PersistentSortedMap;
import org.javersion.util.PersistentTreeMap;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public abstract class VersionGraph<K, V, M,
                          This extends VersionGraph<K, V, M, This, B>,
                          B extends VersionGraphBuilder<K, V, M, This, B>>
        implements Function<Revision, VersionNode<K, V, M>> {

    public final PersistentMap<Revision, VersionNode<K, V, M>> versionNodes;

    private final VersionNode<K, V, M> at;

    private final VersionNode<K, V, M> tip;

    public VersionGraph() {
        this(PersistentTreeMap.<Revision, VersionNode<K, V, M>> empty(), null, null);
    }

    protected VersionGraph(VersionGraphBuilder<K, V, M, This, B> builder) {
        this(builder.versionNodes.toPersistentMap(), builder.tip, builder.at);
    }

    private VersionGraph(PersistentMap<Revision, VersionNode<K, V, M>> versionNodes, VersionNode<K, V, M> tip, VersionNode<K, V, M> at) {
        this.versionNodes = versionNodes;
        this.tip = tip;
        this.at = (at != null ? at : tip);
    }

    public final This commit(Version<K, V, M> version) {
        B builder = newBuilder();
        builder.add(version);
        return builder.build();
    }

    public final This commit(Iterable<? extends Version<K, V, M>> versions) {
        B builder = newBuilder();
        for (Version<K, V, M> version : versions) {
            builder.add(version);
        }
        return builder.build();
    }

    protected abstract B newBuilder();

    protected abstract B newEmptyBuilder();

    @Override
    public final VersionNode<K, V, M> apply(Revision input) {
        return input != null ? getVersionNode(input) : null;
    }

    public final VersionNode<K, V, M> getVersionNode(Revision revision) {
        VersionNode<K, V, M> node = versionNodes.get(revision);
        if (node == null) {
            throw new VersionNotFoundException(revision);
        }
        return node;
    }

    public final Merge<K, V, M> mergeBranches(String... branches) {
        return mergeBranches(asList(branches));
    }

    public final Merge<K, V, M> mergeBranches(Iterable<String> branches) {
        List<VersionMerge<K, V, M>> mergedBranches = Lists.newArrayList();
        for (String branch : branches) {
            mergedBranches.add(new VersionMerge<K, V, M>(getHeads(branch)));
        }
        return new BranchMerge<K, V, M>(mergedBranches);
    }

    public final Merge<K, V, M> mergeRevisions(Revision... revisions) {
        return mergeRevisions(asList(revisions));
    }

    public final Merge<K, V, M> mergeRevisions(Iterable<Revision> revisions) {
        return new VersionMerge<K, V, M>(transform(revisions, this));
    }

    public final Iterable<VersionNode<K, V, M>> getHeads(String branch) {
        return transform(getHeads().range(min(branch), max(branch)), mapValueFunction());
    }

    public final VersionNode<K, V, M> getHead(String branch) {
        return getFirst(transform(getHeads().range(min(branch), max(branch), false), mapValueFunction()), null);
    }

    public final PersistentSortedMap<BranchAndRevision, VersionNode<K, V, M>> getHeads() {
        return at != null ? at.heads : PersistentTreeMap.empty();
    }

    public final Iterable<Revision> getHeadRevisions() {
        return getHeads().valueStream().map(VersionNode::getRevision).collect(Collectors.toList());
    }

    public final This at(Revision revision) {
        return newBuilder().at(getVersionNode(revision)).build();
    }

    public final This atTip() {
        return newBuilder().at(getTip()).build();
    }

    public final boolean isEmpty() {
        return versionNodes.isEmpty();
    }

    public final VersionNode<K, V, M> getTip() {
        return tip;
    }

    public final Set<String> getBranches() {
        return getHeads().keyStream().map(k -> k.branch).collect(toSet());
    }

    /**
     * @return versions in newest first (or reverse topological) order.
     */
    public final Iterable<Version<K, V, M>> getVersions() {
        return Iterables.transform(getVersionNodes(), VersionNode::getVersion);
    }

    /**
     * @return versions in newest first (or reverse topological) order.
     */
    public final Iterable<VersionNode<K, V, M>> getVersionNodes() {
        return new VersionNodeIterable<>(getTip());
    }

    public This optimize(Revision... revisions) {
        return optimize(ImmutableSet.copyOf(revisions));
    }

    public This optimize(Set<Revision> revisions) {
        return optimize(versionNode -> revisions.contains(versionNode.revision));
    }

    public This optimize(Predicate<VersionNode<K, V, M>> revisions) {
        B builder = newEmptyBuilder();
        for (Version<K, V, M> version : new OptimizedGraphBuilder<>(this, revisions).getOptimizedVersions()) {
            builder.add(version);
        }
        return builder.build();
    }
}
