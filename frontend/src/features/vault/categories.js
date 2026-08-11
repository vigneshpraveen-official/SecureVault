// Matches backend/src/main/java/com/securevault/vault/Category.java exactly — keep in sync.
export const CATEGORIES = ['PERSONAL', 'WORK', 'DEVELOPMENT', 'SOCIAL', 'BANKING', 'ENTERTAINMENT', 'OTHER'];

export const CATEGORY_VARIANT = {
  PERSONAL: 'accent',
  WORK: 'neutral',
  DEVELOPMENT: 'success',
  SOCIAL: 'warning',
  BANKING: 'danger',
  ENTERTAINMENT: 'accent',
  OTHER: 'neutral',
};

export const SORT_FIELDS = [
  { value: 'createdAt', label: 'Date added' },
  { value: 'updatedAt', label: 'Last updated' },
  { value: 'title', label: 'Title' },
  { value: 'username', label: 'Username' },
  { value: 'websiteUrl', label: 'Website' },
  { value: 'category', label: 'Category' },
  { value: 'favorite', label: 'Favorite' },
  { value: 'strengthScore', label: 'Strength' },
];
