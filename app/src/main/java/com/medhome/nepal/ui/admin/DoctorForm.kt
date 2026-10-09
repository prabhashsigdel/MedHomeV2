package com.medhome.nepal.ui.admin

import androidx.annotation.StringRes
import com.medhome.nepal.R
import com.medhome.nepal.data.DoctorMapper
import com.medhome.nepal.domain.Doctor
import com.medhome.nepal.domain.Specialty

/** The add / edit doctor form as typed. Numbers stay text until saved. */
data class DoctorFormFields(
    val name: String = "",
    val specialty: Specialty? = null,
    val hospital: String = "",
    val fee: String = "",
    val experience: String = "",
    val bio: String = "",
    val slotMinutes: String = DoctorMapper.DEFAULT_SLOT_MINUTES.toString(),
    val schedule: ScheduleInput = emptyMap(),
)

/** One message per field (null: fine). [schedule] says some opening hours need fixing. */
data class DoctorFormErrors(
    @param:StringRes val name: Int? = null,
    @param:StringRes val specialty: Int? = null,
    @param:StringRes val hospital: Int? = null,
    @param:StringRes val fee: Int? = null,
    @param:StringRes val experience: Int? = null,
    @param:StringRes val bio: Int? = null,
    @param:StringRes val slotMinutes: Int? = null,
    val schedule: Boolean = false,
) {
    val isEmpty: Boolean
        get() = listOf(name, specialty, hospital, fee, experience, bio, slotMinutes).all { it == null } && !schedule
}

/**
 * Validation for the doctor form: the limits of [DoctorMapper] and firestore.rules, so anything
 * it accepts is shown to patients exactly as saved. Text is cleaned the way the mapper cleans it
 * (invisible characters out, spaces collapsed) before its length is checked.
 */
object DoctorForm {
    /** Longest number typed; more digits can't be in range anyway. */
    private const val MAX_NUMBER_DIGITS = 6

    fun fieldsOf(doctor: Doctor): DoctorFormFields = DoctorFormFields(
        name = doctor.name,
        specialty = doctor.specialty,
        hospital = doctor.hospital,
        // Western digits in the fields, like phone numbers: these are typed values, not display text.
        fee = doctor.feeNpr.toString(),
        experience = doctor.experienceYears?.toString().orEmpty(),
        bio = doctor.bio,
        slotMinutes = doctor.slotMinutes.toString(),
        schedule = ScheduleEditor.from(doctor.weeklySchedule),
    )

    fun validate(fields: DoctorFormFields): DoctorFormErrors = DoctorFormErrors(
        name = textError(cleanLine(fields.name), DoctorMapper.MAX_NAME_LENGTH, R.string.admin_error_name_too_long),
        specialty = if (fields.specialty == null) R.string.admin_error_specialty else null,
        hospital = textError(cleanLine(fields.hospital), DoctorMapper.MAX_HOSPITAL_LENGTH, R.string.admin_error_hospital_too_long),
        fee = if (wholeNumber(fields.fee) in 0..DoctorMapper.MAX_FEE_NPR) null else R.string.admin_error_fee,
        experience = if (wholeNumber(fields.experience) in 0..DoctorMapper.MAX_EXPERIENCE_YEARS) null else R.string.admin_error_experience,
        bio = textError(cleanText(fields.bio), DoctorMapper.MAX_BIO_LENGTH, R.string.admin_error_bio_too_long),
        slotMinutes = if (wholeNumber(fields.slotMinutes) in DoctorMapper.SlotMinutesRange) null else R.string.admin_error_slot_minutes,
        schedule = ScheduleEditor.hasProblems(fields.schedule),
    )

    /** The doctor to save under [id], cleaned, or null while the form has errors. */
    fun toDoctor(id: String, fields: DoctorFormFields): Doctor? {
        if (!Doctor.isValidId(id) || !validate(fields).isEmpty) return null
        return Doctor(
            id = id,
            name = cleanLine(fields.name) ?: return null,
            specialty = fields.specialty ?: return null,
            hospital = cleanLine(fields.hospital) ?: return null,
            feeNpr = wholeNumber(fields.fee) ?: return null,
            experienceYears = wholeNumber(fields.experience) ?: return null,
            bio = cleanText(fields.bio) ?: return null,
            slotMinutes = wholeNumber(fields.slotMinutes) ?: return null,
            weeklySchedule = ScheduleEditor.toSchedule(fields.schedule) ?: return null,
        )
    }

    /**
     * A whole number typed in any script's digits (a Nepali keyboard types ०-९), spaces around
     * it ignored; null when empty or not only digits.
     */
    fun wholeNumber(text: String): Int? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_NUMBER_DIGITS) return null
        return trimmed.fold(0) { value, char ->
            val digit = Character.digit(char, RADIX).takeIf { it >= 0 } ?: return null
            value * RADIX + digit
        }
    }

    /** Typed numbers kept to digits only, so a field can't grow past what can be valid. */
    fun numberInput(text: String): String = text.filter(Char::isDigit).take(MAX_NUMBER_DIGITS)

    private fun textError(cleaned: String?, maxLength: Int, @StringRes tooLong: Int): Int? = when {
        cleaned == null -> R.string.admin_error_required
        cleaned.length > maxLength -> tooLong
        else -> null
    }

    // Cleaned without a length cap, so an over-long value is reported rather than cut.
    private fun cleanLine(text: String): String? = DoctorMapper.cleanLine(text, Int.MAX_VALUE)

    private fun cleanText(text: String): String? = DoctorMapper.cleanText(text, Int.MAX_VALUE)

    private const val RADIX = 10
}
