package com.ourgiant.s3.browser.core;

// A minimal (key, size) pairing shared by both inputs BatchDownloadPlanner.plan accepts:
// directly-selected S3Entry objects and RecursiveObjectListing.listAll's S3Object results -
// letting the planner itself stay decoupled from either source type.
public record KeyAndSize(String key, long size) {
}
