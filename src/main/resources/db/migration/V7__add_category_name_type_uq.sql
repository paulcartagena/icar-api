    CREATE UNIQUE INDEX uq_category_name_type
    ON category (lower(name), type);
