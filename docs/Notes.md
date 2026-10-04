# ICAR API — Notes

Design decisions that don't fit in the diagram. See [ERD](./ERD.md) for the data model.

## Donation
- No relation to `Member`: donating is a public action, being a member isn't required.
- `payer_email` is only filled for `PAYPAL` (taken from the PayPal capture).
- No `currency`: everything is USD. If another currency is added, this is the field to reintroduce.
- `registered_by` is `null` when it completes on its own (`PAYPAL`); for `CASH`/`TRANSFER` it points to whoever entered it.

## Member
- `status`: `VISITOR | NEW | IN_DISCIPLESHIP | ACTIVE | INACTIVE | DECEASED`.
- **Family**: `Member.family_id` and `Family.responsible_member_id` are not a problematic circular reference;
  the responsible `Member` must be created first.
- **Ministry**: many-to-many with `joined_at`, so `MemberMinistry` is its own entity with a composite key,
  not a `@ManyToMany`.

## Income
- `source_donation_id`: only set when it comes from a `Donation`; `null` if entered manually.
- `registered_by`: only set when a person entered it; `null` if the system generated it when a donation completed.

## Budget
Planned target for a category in a period, not an actual money movement. The "budget vs. actual" report sums the
period's `Income`/`Expense` and compares it against `planned_amount`.

## Category
- `type` is immutable: changing it would leave `Income`/`Expense`/`Budget` rows pointing at a category of the
  wrong type. To "change" it, create a new one and deactivate the old one.
- Never deleted, only deactivated (`active`); income and expense must reject inactive categories.
- Name is unique per `type`, case-insensitive (`V7`: unique index on `lower(name), type`).

## Other
- **Images**: `image_url` stores S3 URLs; the upload flow is separate and the API only persists the URL.
- **Singletons**: `AboutUs` and `ContactInfo` always have exactly 1 row.
