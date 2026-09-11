# ICAR API — Notes

Design decisions and context that don't fit directly in the diagrams. See [ERD](./ERD.md) for the data model.

## Donation
Deliberately has no relation to `Member`: donating is a public action open to anyone who visits the site, it
doesn't require being a member. The donor only enters their name (`donor_name`) and optionally a message
(`message`); `payer_email` isn't a form field — it's only filled in when the method is PAYPAL, taken from the
PayPal capture response.

## Member.status
Covers a congregation member's lifecycle: visitor, new, in discipleship, active, inactive, or deceased
(`VISITOR | NEW | IN_DISCIPLESHIP | ACTIVE | INACTIVE | DECEASED`). It's deliberately a separate module from
donations.

## Member ↔ Family
`Member.family_id` (which family they belong to) and `Family.responsible_member_id` (who's responsible for that
family) are two separate relationships toward the same pair of tables — it's not a problematic circular
reference, you just need to create the responsible `Member` before (or at the same time as) assigning them as
responsible for their `Family`.

## Member ↔ Ministry
Many-to-many (a member can be in 0, 1, or several ministries), tracked via `member_ministry(member_id,
ministry_id, joined_at)`. Because it carries `joined_at`, it's not a plain join table — it needs its own JPA
entity with a composite key (`member_id` + `ministry_id`), not a bare `@ManyToMany`/`@JoinTable`.

## Income
- `source_donation_id` is optional — null if the income was registered manually (not from a donation).
- `registered_by` is optional — it only has a value when a user (e.g. the treasurer) registers the income by
  hand; when the income is generated automatically because a donation completed, no one registered it, so it
  stays null.

**Why does `Income` have `registered_by` if it comes from a `Donation`?**
It doesn't always come from a donation. `Income` represents *any* money coming into the church, and there are
two ways one can exist:
1. **Automatic**: a `Donation` completes (e.g. the donor paid via PayPal) → the system creates the `Income` on
   its own, with no one touching it. There, `source_donation_id` points to that donation and `registered_by`
   stays null (no one "registered" it, the system generated it).
2. **Manual**: the treasurer receives money that didn't go through the site's donation flow (e.g. someone hands
   them cash, or there's income from another source) and enters it themselves in the admin panel. There,
   `source_donation_id` stays null and `registered_by` points to the user who entered it — useful for knowing
   who's responsible for that record if it ever needs to be audited or corrected.

## Budget
It's a goal/plan for how much money is expected to come in or go out for a category during a period, defined
*before* it happens (e.g. "Missions: $2,000 planned for Q1 2026"). It's not an actual money movement — it's just
a reference number. With that, the "budget vs. actual" report sums the real `Income`/`Expense` for that category
in that period and compares it against the `Budget`'s `planned_amount`, to see whether they're spending/receiving
more, less, or exactly as planned.

## Images
Images (`image_url` in `AboutUs`/`EventPhoto`) are S3 URLs, not binaries — the upload flow is separate
(`POST /api/admin/files/upload`), and this API only persists the resulting URL.

## Singleton records
`AboutUs` and `ContactInfo` are "singleton" records in practice (there's always exactly 1 row), even though
they're modeled as a normal table.
