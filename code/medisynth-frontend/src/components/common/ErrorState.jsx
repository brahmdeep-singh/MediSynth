import { AlertCircle } from "lucide-react";
import Button from "./Button";

export default function ErrorState({ message = "Unable to load records. Please try again.", onRetry }) {
  return (
    <div className="state-block">
      <AlertCircle size={36} color="var(--danger)" />
      <h4>Something went wrong</h4>
      <p>{message}</p>
      {onRetry && <Button variant="secondary" onClick={onRetry}>Try again</Button>}
    </div>
  );
}
