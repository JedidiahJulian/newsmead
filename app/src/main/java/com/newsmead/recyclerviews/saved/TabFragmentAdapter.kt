import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.newsmead.data.StudyConfig
import com.newsmead.fragments.saved.SavedAllFragment
import com.newsmead.fragments.saved.SavedListsFragment
import com.newsmead.fragments.saved.StudyArticlesFragment

public class TabFragmentAdapter(
    fragment: Fragment,
    private val localStudyOnly: Boolean = StudyConfig.ACCOUNT_FREE_STUDY
) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = if (localStudyOnly) 1 else 3

    // Keep local articles distinct from restored account-backed tab fragments.
    override fun getItemId(position: Int): Long = when {
        localStudyOnly -> TESTING_TAB_ID
        position == 2 -> TESTING_TAB_ID
        else -> position.toLong()
    }

    override fun containsItem(itemId: Long): Boolean =
        if (localStudyOnly) itemId == TESTING_TAB_ID else itemId == 0L || itemId == 1L || itemId == TESTING_TAB_ID

    override fun createFragment(position: Int): Fragment {
        if (localStudyOnly) return StudyArticlesFragment()
        return when (position) {
            0 -> SavedListsFragment()
            1 -> SavedAllFragment()
            2 -> StudyArticlesFragment()
            else -> SavedListsFragment()
        }
    }

    companion object {
        const val TESTING_TAB_ID = 100L
    }
}
