// Se carga antes de cada test de Vitest: suma los matchers de jest-dom (toBeInTheDocument, toHaveAttribute…).
import '@testing-library/jest-dom/vitest';
import { afterEach } from 'vitest';
import { cleanup } from '@testing-library/react';

afterEach(() => cleanup());
