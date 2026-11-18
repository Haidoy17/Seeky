package mz.bam.seeky.android;
/** A replaced workspace must reject late callbacks from readers that opened the old one. */
final class WorkspaceEpoch {static volatile long value;static volatile boolean restoring;private WorkspaceEpoch(){}}
