import { Link } from "react-router-dom";
import { Compass } from "lucide-react";
import Button from "../components/common/Button";

export default function NotFoundPage() {
  return (
    <div className="centered-state-page">
      <div className="state-block">
        <Compass size={40} />
        <h4>Page not found</h4>
        <p>The page you're looking for doesn't exist or may have moved.</p>
        <Link to="/"><Button>Return home</Button></Link>
      </div>
    </div>
  );
}
