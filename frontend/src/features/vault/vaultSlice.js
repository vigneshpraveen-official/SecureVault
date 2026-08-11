import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';
import { vaultApi } from '../../api/vault';

export const DEFAULT_QUERY = {
  page: 0,
  size: 20,
  sortBy: 'createdAt',
  direction: 'desc',
  category: '',
  title: '',
  username: '',
  website: '',
};

export const fetchVaultList = createAsyncThunk('vault/list', async (params, { rejectWithValue }) => {
  try {
    // Empty-string filters are the "not set" convention on this form; the backend only wants
    // real params, not category=&title=&... on every request.
    const cleaned = Object.fromEntries(Object.entries(params).filter(([, v]) => v !== ''));
    return await vaultApi.list(cleaned);
  } catch (error) {
    return rejectWithValue(error);
  }
});

export const createCredential = createAsyncThunk(
  'vault/create',
  async (payload, { dispatch, getState, rejectWithValue }) => {
    try {
      const result = await vaultApi.create(payload);
      dispatch(fetchVaultList(getState().vault.query));
      return result;
    } catch (error) {
      return rejectWithValue(error);
    }
  },
);

export const updateCredential = createAsyncThunk(
  'vault/update',
  async ({ id, payload }, { dispatch, getState, rejectWithValue }) => {
    try {
      const result = await vaultApi.update(id, payload);
      dispatch(fetchVaultList(getState().vault.query));
      return result;
    } catch (error) {
      return rejectWithValue(error);
    }
  },
);

export const deleteCredential = createAsyncThunk(
  'vault/delete',
  async (id, { dispatch, getState, rejectWithValue }) => {
    try {
      await vaultApi.remove(id);
      dispatch(fetchVaultList(getState().vault.query));
      return id;
    } catch (error) {
      return rejectWithValue(error);
    }
  },
);

export const restoreCredential = createAsyncThunk(
  'vault/restore',
  async (id, { dispatch, getState, rejectWithValue }) => {
    try {
      const result = await vaultApi.restore(id);
      dispatch(fetchVaultList(getState().vault.query));
      return result;
    } catch (error) {
      return rejectWithValue(error);
    }
  },
);

const initialState = {
  query: DEFAULT_QUERY,
  page: null,
  status: 'idle',
  error: null,
};

const vaultSlice = createSlice({
  name: 'vault',
  initialState,
  reducers: {
    setQuery(state, action) {
      state.query = { ...state.query, ...action.payload };
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchVaultList.pending, (state) => {
        state.status = 'loading';
        state.error = null;
      })
      .addCase(fetchVaultList.fulfilled, (state, action) => {
        state.status = 'idle';
        state.page = action.payload;
      })
      .addCase(fetchVaultList.rejected, (state, action) => {
        state.status = 'idle';
        state.error = action.payload;
      });
  },
});

export const { setQuery } = vaultSlice.actions;
export default vaultSlice.reducer;
