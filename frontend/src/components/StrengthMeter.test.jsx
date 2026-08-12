import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import StrengthMeter from './StrengthMeter';

describe('StrengthMeter', () => {
  it('should_renderTheLevelLabelAndFeedbackList_from_theGivenScoreAndFeedback', () => {
    render(
      <StrengthMeter
        score={3}
        strength="Medium"
        entropyBits={42.3}
        feedback={['Increase length to 16+ characters for extra safety margin', 'Add a special character']}
      />,
    );

    expect(screen.getByText('Medium')).toBeInTheDocument();
    expect(screen.getByText('42.3 bits entropy')).toBeInTheDocument();
    expect(
      screen.getByText('Increase length to 16+ characters for extra safety margin'),
    ).toBeInTheDocument();
    expect(screen.getByText('Add a special character')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Password strength: Medium' })).toBeInTheDocument();
  });

  it('should_renderNoFeedbackList_when_feedbackIsEmpty', () => {
    render(<StrengthMeter score={5} strength="Very Strong" entropyBits={80} feedback={[]} />);

    expect(screen.getByText('Very Strong')).toBeInTheDocument();
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
  });
});
