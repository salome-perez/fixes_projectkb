public class TreeList {
        public void remove() {
            checkModCount();
            if (currentIndex == -1) {
                throw new IllegalStateException();
            }
            parent.remove(currentIndex);
            if (nextIndex != currentIndex) {
                // remove() following next()
                nextIndex--;
            }
            // the AVL node referenced by next may have become stale after a remove
            // reset it now: will be retrieved by next call to next()/previous() via nextIndex
            next = null;
            current = null;
            currentIndex = -1;
            expectedModCount++;
        }

}