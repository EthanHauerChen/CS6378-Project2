public class Request implements Comparable {
        int nodeNumber;
        int timestamp;
        
        public Request(int nodenum, int timestamp) {
            this.nodeNumber = nodenum;
            this.timestamp = timestamp;
        }

        @Override
        public int compareTo(Object o) {
            Request temp = (Request)o;
            if (this.timestamp < temp.timestamp) return -1;
            else if (this.timestamp > temp.timestamp) return 1;
            else if (this.nodeNumber < temp.nodeNumber) return -1;
            else return 1;
        }

        @Override
        public boolean equals(Object o) {
            Request temp = (Request)o;
            return this.nodeNumber == temp.nodeNumber; //impossible for multiple requests from same node at the same time since csEnter is blocking
        }

        @Override
        public String toString() {
            return "(node: " + this.nodeNumber + ", timestamp: " + this.timestamp + ")";
        }
    }