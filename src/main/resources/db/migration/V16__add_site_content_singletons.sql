-- The church has a single "about us" and a single contact record (also handled in SiteContentService):
-- a unique index on a constant expression allows at most one row per table
CREATE UNIQUE INDEX uq_about_us_singleton ON about_us ((true));
CREATE UNIQUE INDEX uq_contact_info_singleton ON contact_info ((true));
